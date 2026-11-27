// Run: cd firebase-tests && npm i && npx firebase emulators:exec --only firestore --project demo-tripguide "node --test rules.test.mjs"
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, arrayUnion } from "firebase/firestore";
import { readFileSync } from "node:fs";
import { test, before, after, beforeEach } from "node:test";

let env;
before(async () => { env = await initializeTestEnvironment({ projectId: "demo-tripguide", firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") } }); });
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const alice = () => env.authenticatedContext("alice").firestore();
const bob = () => env.authenticatedContext("bob").firestore();
const anon = () => env.unauthenticatedContext().firestore();
const reel = (by, code = "C9xYz_12ab") => ({ url: `https://www.instagram.com/reel/${code}/`, placeKey: "kerala-munnar", placeName: "Munnar",
  caption: "Sunrise", author: "x", summary: "", tips: [], addedBy: by, createdAt: 1, reportedBy: [] });
const place = (by) => ({ name: "Hidden falls", region: "Idukki", lat: 10, lng: 77, photos: [], description: "d", guide: "", tips: "", addedBy: by, reportedBy: [] });

test("anyone can read; only signed-in users write, as themselves", async () => {
  await assertSucceeds(getDoc(doc(anon(), "reels/C9xYz_12ab")));
  await assertFails(setDoc(doc(anon(), "reels/C9xYz_12ab"), reel("alice")));
  await assertFails(setDoc(doc(bob(), "reels/C9xYz_12ab"), reel("alice")));
  await assertSucceeds(setDoc(doc(alice(), "reels/C9xYz_12ab"), reel("alice")));
});

test("reel doc id must match its Instagram URL and start unreported", async () => {
  await assertFails(setDoc(doc(alice(), "reels/OTHER123"), reel("alice")));
  await assertFails(setDoc(doc(alice(), "reels/C9xYz_12ab"), { ...reel("alice"), url: "https://evil.com/reel/C9xYz_12ab/" }));
  await assertFails(setDoc(doc(alice(), "reels/C9xYz_12ab"), { ...reel("alice"), reportedBy: ["bob"] }));
});

test("others can only report, once each; nobody can edit someone else's reel", async () => {
  await setDoc(doc(alice(), "reels/C9xYz_12ab"), reel("alice"));
  await assertFails(updateDoc(doc(bob(), "reels/C9xYz_12ab"), { caption: "spam" }));
  await assertSucceeds(updateDoc(doc(bob(), "reels/C9xYz_12ab"), { reportedBy: arrayUnion("bob") }));
  await assertFails(updateDoc(doc(bob(), "reels/C9xYz_12ab"), { reportedBy: ["bob", "carol", "dave"] }));
  await assertFails(deleteDoc(doc(bob(), "reels/C9xYz_12ab")));
  await assertSucceeds(deleteDoc(doc(alice(), "reels/C9xYz_12ab")));
});

test("places: validated, owner-only edits, max 3 photos, reports can't be cleared", async () => {
  await assertFails(setDoc(doc(alice(), "places/p1"), { ...place("alice"), lat: 200 }));
  await assertFails(setDoc(doc(alice(), "places/p1"), { ...place("alice"), photos: ["a", "b", "c", "d"] }));
  await assertSucceeds(setDoc(doc(alice(), "places/p1"), place("alice")));
  await assertFails(updateDoc(doc(bob(), "places/p1"), { name: "Mine now" }));
  await assertSucceeds(updateDoc(doc(bob(), "places/p1"), { reportedBy: arrayUnion("bob") }));
  await assertFails(updateDoc(doc(alice(), "places/p1"), { reportedBy: [] }));
  await assertSucceeds(updateDoc(doc(alice(), "places/p1"), { description: "better" }));
});

test("summaries must be attributed to the writer", async () => {
  const s = { summary: "x", reelCount: 1, updatedBy: "alice" };
  await assertSucceeds(setDoc(doc(alice(), "summaries/kerala-munnar"), s));
  await assertFails(setDoc(doc(bob(), "summaries/kerala-munnar"), s));
  await assertFails(setDoc(doc(anon(), "summaries/kerala-munnar"), { ...s, updatedBy: "x" }));
});
