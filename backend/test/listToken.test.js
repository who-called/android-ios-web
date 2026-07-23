import { test } from "node:test";
import assert from "node:assert/strict";
import { issueListToken, verifyListToken, LIST_TOKEN_TTL_S } from "../src/listToken.js";

test("a fresh token verifies", () => {
  const { token } = issueListToken();
  assert.equal(verifyListToken(token), true);
});

test("an expired token is rejected", () => {
  const past = Date.now() - (LIST_TOKEN_TTL_S + 5) * 1000;
  const { token } = issueListToken(past);
  assert.equal(verifyListToken(token), false);
});

test("a token remains valid within its TTL", () => {
  const { token } = issueListToken();
  const justBeforeExpiry = Date.now() + (LIST_TOKEN_TTL_S - 5) * 1000;
  assert.equal(verifyListToken(token, justBeforeExpiry), true);
});

test("tampered tokens are rejected", () => {
  const { token } = issueListToken();
  const [exp, sig] = token.split(".");
  // Extend expiry without re-signing.
  assert.equal(verifyListToken(`${Number(exp) + 3600}.${sig}`, Date.now()), false);
  // Broken signature.
  assert.equal(verifyListToken(`${exp}.AAAA${sig.slice(4)}`), false);
});

test("garbage input is rejected", () => {
  for (const bad of ["", ".", "abc", "123", "123.", ".sig", null, undefined, 42]) {
    assert.equal(verifyListToken(bad), false);
  }
});
