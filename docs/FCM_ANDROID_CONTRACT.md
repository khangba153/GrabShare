# GrabShare FCM contract

This file is the integration contract between the Android FCM work and the Cloud Function implementation.

## Firebase Installation ID schema

Android writes one FID per authenticated UID:

`fcmInstallations/{uid}`

Fields:
- `fid`: Firebase Installation ID returned by FCM's `onRegistered()` callback / Firebase Installations SDK
- `updatedAt`: Firestore server timestamp

Client security rules allow an authenticated user to get, create, update, or delete only their own token document. Listing tokens is forbidden. Cloud Functions should use Firebase Admin SDK.

## Trigger

Listen only for document creation:

`notifications/{notificationId}`

The current app writes these notification types:
- `REQUEST_SENT`
- `REQUEST_ACCEPTED`
- `REQUEST_REJECTED`
- `REQUEST_CANCELLED`
- `TRIP_CANCELLED`
- `TRIP_COMPLETED`

Do not write another document to `notifications` from this trigger; it can trigger itself recursively. A read-state update must not send another push, so use an on-create trigger rather than on-write.

## Receiver contract

Look up `fcmTokens/{recipientId}` where `recipientId` comes from the new notification document. If no token exists, return without sending.

Send a **data-only** FCM message. Do not include the FCM `notification` property, because Android needs to validate that the message belongs to the currently signed-in UID before showing it.

Required data fields (all values must be strings):

```json
{
  "recipientId": "<notifications/{id}.recipientId>",
  "notificationId": "<document id from notifications/{id}>",
  "type": "REQUEST_ACCEPTED",
  "title": "Yêu cầu đã được chấp nhận",
  "body": "Yêu cầu đi chung của bạn đã được chấp nhận."
}
```

Use Android high priority for these user-visible, time-sensitive messages so data-only messages can be delivered more reliably while the app is backgrounded. Keep payload values small and do not include personal or trip details that are not necessary for the lock-screen notification.

Android opens NotificationsActivity when the user taps the system notification. The notification ID is used as the local system notification key, so retries for the same document replace rather than create a separate visible notification.

## Delivery caveats

Firestore event triggers can be delivered more than once. If duplicate sends must be prevented at the server side, add an idempotency mechanism keyed by notification document ID. FCM itself should not be treated as a guaranteed exactly-once delivery mechanism.
