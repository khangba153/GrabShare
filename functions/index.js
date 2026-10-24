import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

initializeApp();

const firestore = getFirestore();

/**
 * IMPORTANT:
 * Change this to the nearest supported Cloud Functions region for the
 * project's existing Firestore database before deploying.
 * Check Firebase Console > Firestore Database > database location.
 *
 * Singapore is a reasonable starting point for a Vietnam-based team only
 * when it also suits the Firestore database location.
 */
const FIRESTORE_FUNCTION_REGION = "asia-southeast1";
const MAX_INSTANCES = 1;

/**
 * Notification documents currently created by GrabShare.
 * Keep these titles and bodies aligned with the Android inbox/service.
 */
const NOTIFICATION_CONTENT = Object.freeze({
  REQUEST_SENT: {
    title: "Có yêu cầu đi chung mới",
    body: "Bạn có một yêu cầu đi chung cần xem."
  },
  REQUEST_ACCEPTED: {
    title: "Yêu cầu đã được chấp nhận",
    body: "Yêu cầu đi chung của bạn đã được chấp nhận."
  },
  REQUEST_REJECTED: {
    title: "Yêu cầu bị từ chối",
    body: "Yêu cầu đi chung của bạn đã bị từ chối."
  },
  REQUEST_CANCELLED: {
    title: "Yêu cầu đã bị hủy",
    body: "Một yêu cầu đi chung đã bị hủy."
  },
  TRIP_CANCELLED: {
    title: "Chuyến đi đã bị hủy",
    body: "Chuyến đi liên quan đến yêu cầu của bạn đã bị hủy."
  },
  TRIP_COMPLETED: {
    title: "Chuyến đi đã hoàn thành",
    body: "Chuyến đi liên quan đến yêu cầu của bạn đã hoàn thành."
  }
});

function isNonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

/**
 * Fire only when an inbox document is created.
 *
 * FCM message is data-only so the Android service can verify recipientId
 * against the currently signed-in UID before showing the system notification.
 */
export const sendPushOnNotificationCreated = onDocumentCreated(
  {
    document: "notifications/{notificationId}",
    region: FIRESTORE_FUNCTION_REGION,
    maxInstances: MAX_INSTANCES,

    // The in-app inbox remains the source of truth if this one push fails.
    // We don't auto-retry permanent bad-FID/config errors in this MVP.
    retry: false
  },
  async (event) => {
    const notificationSnapshot = event.data;

    if (!notificationSnapshot) {
      logger.warn("FCM push skipped: event has no document snapshot.", {
        notificationId: event.params.notificationId
      });
      return;
    }

    const notification = notificationSnapshot.data();
    const notificationId = event.params.notificationId;
    const recipientId = notification.recipientId;
    const type = notification.type;

    if (!isNonEmptyString(recipientId)) {
      logger.warn("FCM push skipped: recipientId is missing.", {
        notificationId
      });
      return;
    }

    const content = NOTIFICATION_CONTENT[type];

    if (!content) {
      logger.info("FCM push skipped: unsupported notification type.", {
        notificationId,
        type
      });
      return;
    }

    const installationSnapshot = await firestore
      .collection("fcmInstallations")
      .doc(recipientId)
      .get();

    if (!installationSnapshot.exists) {
      logger.warn("FCM push skipped: recipient has no FID document.", {
        notificationId,
        recipientId,
        type
      });
      return;
    }

    const fid = installationSnapshot.get("fid");

    if (!isNonEmptyString(fid)) {
      logger.warn("FCM push skipped: recipient FID is missing.", {
        notificationId,
        recipientId,
        type
      });
      return;
    }

    const message = {
      fid,
      data: {
        recipientId,
        notificationId,
        type,
        title: content.title,
        body: content.body
      },
      android: {
        priority: "high"
      }
    };

    try {
      const messageId = await getMessaging().send(message);

      logger.info("FCM push sent.", {
        notificationId,
        recipientId,
        type,
        messageId
      });
    } catch (error) {
      logger.error("FCM push send failed.", {
        notificationId,
        recipientId,
        type,
        errorCode: error?.code ?? "unknown",
        errorMessage: error?.message ?? String(error)
      });

      throw error;
    }
  }
);
