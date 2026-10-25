const NOTIFICATION_CONTENT = Object.freeze({
  REQUEST_SENT: Object.freeze({
    title: "Có yêu cầu đi chung mới",
    body: "Bạn có một yêu cầu đi chung cần xem."
  }),
  REQUEST_ACCEPTED: Object.freeze({
    title: "Yêu cầu đã được chấp nhận",
    body: "Yêu cầu đi chung của bạn đã được chấp nhận."
  }),
  REQUEST_REJECTED: Object.freeze({
    title: "Yêu cầu bị từ chối",
    body: "Yêu cầu đi chung của bạn đã bị từ chối."
  }),
  REQUEST_CANCELLED: Object.freeze({
    title: "Yêu cầu đã bị hủy",
    body: "Một yêu cầu đi chung đã bị hủy."
  }),
  TRIP_CANCELLED: Object.freeze({
    title: "Chuyến đi đã bị hủy",
    body: "Chuyến đi liên quan đến yêu cầu của bạn đã bị hủy."
  }),
  TRIP_COMPLETED: Object.freeze({
    title: "Chuyến đi đã hoàn thành",
    body: "Chuyến đi liên quan đến yêu cầu của bạn đã hoàn thành."
  })
});

/** Return user-facing notification copy for a supported notification type. */
export function getNotificationContent(type) {
  if (typeof type !== "string") {
    return null;
  }

  return NOTIFICATION_CONTENT[type] || null;
}

/** Validate required IDs without coercing arbitrary Firestore values to strings. */
export function isNonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}
