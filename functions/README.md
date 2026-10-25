# GrabShare Cloud Function: FCM push

Function: `sendPushOnNotificationCreated`

Trigger: create event on `notifications/{notificationId}`.

## How it works

1. A GrabShare feature creates a document in `notifications`.
2. The function reads its `recipientId` and `type`.
3. It resolves `fcmInstallations/{recipientId}.fid`.
4. It sends a data-only FCM message to that FID.
5. Android checks that `recipientId` matches the signed-in UID, then displays the notification.

The function does not create/update documents in `notifications`, so it cannot trigger itself from its own work. The in-app notification document remains the source of truth if push delivery fails.

Supported notification types:
- `REQUEST_SENT`
- `REQUEST_ACCEPTED`
- `REQUEST_REJECTED`
- `REQUEST_CANCELLED`
- `TRIP_CANCELLED`
- `TRIP_COMPLETED`

## Before deploying

1. Confirm the Firebase project is `grabshare`.
2. Open Firebase Console > Firestore Database and confirm the database location.
3. In `index.js`, change `FIRESTORE_FUNCTION_REGION` to the closest supported Cloud Functions region for that database. The current value `asia-southeast1` is only a default for the team's likely location; do not assume it matches the existing database.
4. Confirm `fcmInstallations/{uid}` stores `fid`. This function uses the current Firebase Admin SDK FID target field, not the deprecated registration-token field.
5. Confirm the latest Android PR has been merged or the app version being tested is built from the branch containing the FID registration code.
6. The Firebase project must be on the Blaze (pay-as-you-go) plan to deploy Cloud Functions. Do not change billing without the project owner/team's approval.

## Install and test locally

Install Node.js 22 and the current Firebase CLI, then from the repository root run:

```sh
cd functions
npm install
npm run lint
npm test
cd ..
firebase login
firebase use --add
```

When `firebase use --add` asks for a project, select the existing `grabshare` project. Check the selected project before any deploy.

## Deploy only this function

After checking the billing plan, project ID, database region, and code review:

```sh
firebase deploy --only functions:sendPushOnNotificationCreated
```

This command deploys only the FCM function; it does not deploy Firestore Rules or indexes.

## Check logs

After creating a new notification through the app, open Firebase Console > Functions > Logs or Google Cloud Logs Explorer. Search for:
- `FCM push sent.`
- `FCM push skipped: recipient has no FID document.`
- `FCM push send failed.`

A successful send means FCM accepted the message; it does not guarantee the device displayed it.

## Cost notes

FCM itself is free. Cloud Functions deployment requires Blaze billing. The free usage quota includes up to 2 million function invocations per month, with additional free compute and networking quotas. Function deployment can also use Cloud Build and Artifact Registry resources; some storage or related Google Cloud usage can be billable. For a student MVP with low volume, normal invocations are likely to stay inside the free quota, but a zero bill is not guaranteed. Set a billing budget alert before deployment and monitor actual usage. A budget alert notifies you; it does not automatically stop spending.

## Retry behavior

This MVP uses `retry: false` so an invalid FID or permanent configuration issue does not repeatedly invoke the function. If sending fails, the in-app notification still exists in Firestore and the error is logged. If the team later needs guaranteed retry behavior, add deliberate retry and idempotency logic first; Firestore background events can be delivered more than once.
