const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "sandeepsahukumar54321@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("journeys").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("sos_sessions").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("emergency_facilities").where("activeStatus", "==", true).get());
});

test("UserProfile: owner can create/read/update, rejects ghost field and cross-user read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com", email_verified: true }).firestore();

  const validProfile = {
    userId: ALICE_UID,
    displayName: "Alice",
    email: "alice@test.com",
    language: "en",
    deviationThresholdMeters: 250,
    retentionDays: 30,
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow/ghost field attack
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile,
      isAdmin: true,
    })
  );
});

test("Trusted contacts: owner CRUD (add, update phone & email, delete) succeeds; cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const contactRef = aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc("c1");
  await assertSucceeds(
    contactRef.set({
      ownerUserId: ALICE_UID,
      name: "Meera",
      phoneNumber: "+919876543210",
      email: "meera@example.com",
      contactChannel: "+919876543210",
      channelType: "PHONE",
      relationship: "Sister",
      verified: true,
      notifyOnStart: true,
      notifyOnSos: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    contactRef.update({
      phoneNumber: "+919999888877",
      email: "meera.new@example.com",
      contactChannel: "+919999888877",
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("contacts").doc("c1").update({
      phoneNumber: "+910000000000",
      updatedAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").doc("c1").delete());

  await assertSucceeds(contactRef.delete());
});

test("Journeys: state machine, query alignment, cross-user isolation, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const journeyPayload = {
    userId: ALICE_UID,
    ownerDisplayName: "Alice",
    originLabel: "Connaught Place",
    originLat: 28.6315,
    originLon: 77.2167,
    destinationLabel: "Hauz Khas Metro",
    destinationLat: 28.5433,
    destinationLon: 77.2066,
    selectedRouteId: "route_1",
    selectedRouteLabel: "Main Lit Avenue",
    providerName: "OSRM",
    state: "ACTIVE",
    deviationThresholdMeters: 250,
    sharingEnabled: true,
    sharedContactCount: 1,
    shareTokenId: "tok_abcdef1234567890",
    lastLat: 28.6315,
    lastLon: 77.2167,
    lastAccuracyMeters: 12.0,
    lastLocationAt: tsNow(),
    startedAt: tsNow(),
    createdAt: tsNow(),
    updatedAt: tsNow(),
  };

  await assertSucceeds(aliceDb.collection("journeys").doc("j1").set(journeyPayload));
  await assertSucceeds(aliceDb.collection("journeys").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("journeys").get()); // missing userId filter
  await assertFails(bobDb.collection("journeys").doc("j1").get());

  // Append location point
  await assertSucceeds(
    aliceDb.collection("journeys").doc("j1").collection("locations").doc("pt_1").set({
      userId: ALICE_UID,
      journeyId: "j1",
      lat: 28.6200,
      lon: 77.2150,
      accuracyMeters: 10.5,
      sequence: 1,
      recordedAt: tsNow(),
    })
  );

  // Transition to terminal state COMPLETED
  await assertSucceeds(
    aliceDb.collection("journeys").doc("j1").update({
      state: "COMPLETED",
      endedAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Terminal state lock: subsequent update MUST fail
  await assertFails(
    aliceDb.collection("journeys").doc("j1").update({
      state: "ACTIVE",
      updatedAt: tsNow(),
    })
  );
});

test("Share tokens: holder of exact high-entropy token can read; list is owner-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const tokenId = "tok_high_entropy_1234567890abcdef";

  await assertSucceeds(
    aliceDb.collection("share_tokens").doc(tokenId).set({
      tokenHash: "a".repeat(64),
      journeyId: "j1",
      ownerUserId: ALICE_UID,
      ownerDisplayName: "Alice",
      destinationLabel: "Hauz Khas Metro",
      journeyState: "ACTIVE",
      lastLat: 28.63,
      lastLon: 77.21,
      lastLocationText: "Near Mandi House",
      revoked: false,
      expiresAtMillis: Date.now() + 3600000,
      lastUpdatedAt: tsNow(),
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Bob can get exact high-entropy token while active, but cannot list all share_tokens
  await assertSucceeds(bobDb.collection("share_tokens").doc(tokenId).get());
  await assertFails(bobDb.collection("share_tokens").get());
  await assertFails(bobDb.collection("share_tokens").doc(tokenId).update({ revoked: true }));

  // Alice revokes immediately; Bob can no longer read
  await assertSucceeds(
    aliceDb.collection("share_tokens").doc(tokenId).update({
      revoked: true,
      updatedAt: tsNow(),
    })
  );
  await assertFails(bobDb.collection("share_tokens").doc(tokenId).get());
});

test("SOS sessions: owner creates active SOS, streams live location, terminal lock prevents reopening, delete forbidden", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("sos_sessions").doc("sos_1").set({
      userId: ALICE_UID,
      journeyId: "j1",
      shareTokenId: "tok_sos_live_1234567890",
      dedupeKey: "dedupe_1",
      status: "ACTIVE",
      lat: 28.63,
      lon: 77.21,
      locationLabel: "Connaught Place",
      deliverySummary: "Sent to 2 contacts",
      recipientCount: 2,
      acknowledgedCount: 0,
      lastLocationAt: tsNow(),
      activatedAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Owner can stream real-time coordinates while ACTIVE
  await assertSucceeds(
    aliceDb.collection("sos_sessions").doc("sos_1").update({
      lat: 28.6345,
      lon: 77.2190,
      locationLabel: "Live GPS (28.6345, 77.2190)",
      lastLocationAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Cross-user cannot read or update Alice's SOS
  await assertFails(bobDb.collection("sos_sessions").doc("sos_1").get());
  await assertFails(
    bobDb.collection("sos_sessions").doc("sos_1").update({
      lat: 28.0,
      lon: 77.0,
      updatedAt: tsNow(),
    })
  );
  await assertFails(aliceDb.collection("sos_sessions").doc("sos_1").delete());

  await assertSucceeds(
    aliceDb.collection("sos_sessions").doc("sos_1").update({
      status: "RESOLVED",
      resolvedAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    aliceDb.collection("sos_sessions").doc("sos_1").update({
      status: "ACTIVE",
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    aliceDb.collection("sos_sessions").doc("sos_1").update({
      lat: 28.64,
      lon: 77.22,
      updatedAt: tsNow(),
    })
  );
});

test("Admin RBAC & Email Spoofing Protection: unverified email rejected, verified owner succeeds", async () => {
  const spoofedDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  await assertFails(
    spoofedDb.collection("admins").doc(ADMIN_UID).set({
      email: ADMIN_EMAIL,
      role: "OPS_ADMIN",
      grantedAt: tsNow(),
    })
  );

  const realAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    realAdminDb.collection("admins").doc(ADMIN_UID).set({
      email: ADMIN_EMAIL,
      role: "OPS_ADMIN",
      grantedAt: tsNow(),
    })
  );

  // Non-admin cannot moderate safety reports or create emergency facilities
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("safety_reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      category: "POOR_LIGHTING",
      title: "Broken streetlights",
      description: "Streetlights out near metro exit 2.",
      locationLabel: "Metro Exit 2",
      lat: 28.55,
      lon: 77.20,
      status: "PENDING_REVIEW",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertFails(
    aliceDb.collection("safety_reports").doc("rep_1").update({
      status: "VERIFIED",
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    aliceDb.collection("safety_reports").doc("rep_1").update({
      status: "FLAGGED",
      moderationNote: "Attempted non-admin flag",
      moderatedBy: ALICE_UID,
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    aliceDb.collection("safety_reports").doc("rep_1").update({
      status: "RESOLVED",
      moderationNote: "Attempted non-admin resolve",
      moderatedBy: ALICE_UID,
      updatedAt: tsNow(),
    })
  );

  // Verified Admin can list all reports, flag, verify, and resolve safety reports
  await assertSucceeds(realAdminDb.collection("safety_reports").get());
  await assertSucceeds(
    realAdminDb.collection("safety_reports").doc("rep_1").update({
      status: "FLAGGED",
      moderationNote: "Flagged for urgent municipal lighting audit",
      moderatedBy: ADMIN_UID,
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(
    realAdminDb.collection("safety_reports").doc("rep_1").update({
      status: "VERIFIED",
      moderationNote: "Confirmed with municipal log",
      moderatedBy: ADMIN_UID,
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(
    realAdminDb.collection("safety_reports").doc("rep_1").update({
      status: "RESOLVED",
      moderationNote: "Streetlights restored and patrol notified",
      moderatedBy: ADMIN_UID,
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    realAdminDb.collection("emergency_facilities").doc("fac_police_1").set({
      type: "POLICE",
      name: "Connaught Place Police Station",
      phone: "112",
      address: "Baba Kharak Singh Marg, New Delhi",
      lat: 28.6304,
      lon: 77.2137,
      verifiedSource: "Delhi Police Official Directory",
      verifiedAtLabel: "2026-11-10",
      activeStatus: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    realAdminDb.collection("emergency_facilities").doc("fac_amb_1").set({
      type: "AMBULANCE",
      name: "AIIMS Trauma Ambulance Dispatch",
      phone: "108",
      address: "Ansari Nagar East, New Delhi",
      lat: 28.5672,
      lon: 77.2100,
      verifiedSource: "National Health Mission",
      verifiedAtLabel: "2026-11-12",
      activeStatus: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    realAdminDb.collection("emergency_facilities").doc("fac_women_1").set({
      type: "WOMENS_SUPPORT",
      name: "Sakhi One Stop Women Support Center",
      phone: "181",
      address: "Barakhamba Road, New Delhi",
      lat: 28.6290,
      lon: 77.2245,
      verifiedSource: "MWCD Sakhi Directory",
      verifiedAtLabel: "2026-11-12",
      activeStatus: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Authenticated traveller can list active directory entries, cannot mutate them
  await assertSucceeds(
    aliceDb.collection("emergency_facilities").where("activeStatus", "==", true).get()
  );
  await assertFails(
    aliceDb.collection("emergency_facilities").doc("fac_unverified").set({
      type: "POLICE",
      name: "Fake Station",
      phone: "9999999999",
      address: "Unknown",
      lat: 28.6,
      lon: 77.2,
      verifiedSource: "Unverified",
      verifiedAtLabel: "2026-11-12",
      activeStatus: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
});
