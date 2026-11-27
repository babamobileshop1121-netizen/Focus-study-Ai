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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

// 1. Unauthenticated checks
test("Unauthenticated access is denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("focus_logs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("quizzes").get());
});

// 2. User Profile CRUD and bounds
test("Alice can create, read, and update her own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Valid profile creation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      streakDays: 3,
      totalFocusMinutes: 120,
      totalQuizzesTaken: 4,
      averageQuizScore: 85,
      lastActiveDate: "2026-11-25",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can read her profile
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Alice cannot list all users (PII protection)
  await assertFails(aliceDb.collection("users").get());

  // Alice can update her streak and focus minutes
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      streakDays: 4,
      totalFocusMinutes: 150,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice cannot mutate createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 5000),
    })
  );
});

// 3. Cross-user isolation
test("Bob cannot read or alter Alice's profile or data", async () => {
  const now = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      streakDays: 3,
      totalFocusMinutes: 120,
      totalQuizzesTaken: 4,
      averageQuizScore: 85,
      lastActiveDate: "2026-11-25",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" })
  );
});

// 4. Study Sessions CRUD
test("Alice can manage her study sessions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Create session
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").set({
      id: "session_1",
      userId: ALICE_UID,
      subject: "Mathematics",
      chapter: "Calculus Limits",
      scheduledDate: "2026-11-26",
      scheduledTime: "10:00",
      durationMinutes: 60,
      isCompleted: false,
      isRecurring: false,
      recurrenceRule: "NONE",
      notes: "Review practice questions 1 to 10",
      createdAt: now,
      updatedAt: now,
    })
  );

  // List sessions
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );

  // Update session to completed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("session_1").update({
      isCompleted: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

// 5. Focus logs immutability
test("Focus logs can be created but cannot be modified", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Create log
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("focus_logs").doc("log_1").set({
      id: "log_1",
      userId: ALICE_UID,
      subject: "Physics",
      targetMinutes: 45,
      actualMinutes: 45,
      blockedAppsCount: 3,
      completed: true,
      date: "2026-11-25",
      createdAt: now,
    })
  );

  // Mutation is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("focus_logs").doc("log_1").update({
      actualMinutes: 100,
    })
  );
});

// 6. Quizzes creation and reading
test("Quizzes can be created and listed by owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("quizzes").doc("quiz_1").set({
      id: "quiz_1",
      userId: ALICE_UID,
      title: "Cell Biology Quiz",
      subject: "Biology",
      questionCount: 5,
      score: 4,
      totalQuestions: 5,
      accuracy: 80,
      questionsJson: JSON.stringify([{ question: "What is mitochondria?", answer: "Powerhouse" }]),
      sourceType: "PHOTO",
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("quizzes").get()
  );
});
