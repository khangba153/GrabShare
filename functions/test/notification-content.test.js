import test from "node:test";
import assert from "node:assert/strict";
import {
  getNotificationContent,
  isNonEmptyString
} from "../notification-content.js";

test("all supported GrabShare notification types have title and body", () => {
  const types = [
    "REQUEST_SENT",
    "REQUEST_ACCEPTED",
    "REQUEST_REJECTED",
    "REQUEST_CANCELLED",
    "TRIP_CANCELLED",
    "TRIP_COMPLETED"
  ];

  for (const type of types) {
    const content = getNotificationContent(type);

    assert.ok(content, type + " should be supported");
    assert.equal(typeof content.title, "string");
    assert.ok(content.title.trim().length > 0);
    assert.equal(typeof content.body, "string");
    assert.ok(content.body.trim().length > 0);
  }
});

test("unsupported notification types return null", () => {
  assert.equal(getNotificationContent("UNKNOWN_TYPE"), null);
  assert.equal(getNotificationContent(null), null);
  assert.equal(getNotificationContent(undefined), null);
});

test("required IDs must be non-empty strings", () => {
  assert.equal(isNonEmptyString("uid-123"), true);
  assert.equal(isNonEmptyString("  uid-123  "), true);
  assert.equal(isNonEmptyString(""), false);
  assert.equal(isNonEmptyString("   "), false);
  assert.equal(isNonEmptyString(null), false);
  assert.equal(isNonEmptyString(123), false);
});
