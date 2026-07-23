import Foundation
import UserNotifications

/// Opt-in daily "come back and play" reminder (local notification). Kind and
/// encouraging (LinkedIn-style), never guilt-trippy. The user's choice is
/// respected: turning it off removes the scheduled reminder entirely.
///
/// iOS can't conditionally skip a fired local notification the way Android's
/// WorkManager can, so this is a simple daily nudge at a fixed evening hour; the
/// message is re-rolled each app launch for variety.
enum GameReminders {
  static let identifier = "who_called_game_reminder"
  private static let hour = 19  // 7pm local — an evening downtime nudge

  private static let messages: [(title: String, body: String)] = [
    ("🧩 Le défi TRACE du jour t'attend", "5 grilles, 3 minutes — grimpe au classement à ton rythme."),
    ("🔥 Garde ta série en vie", "Une partie rapide suffit pour continuer sur ta lancée."),
    ("🏆 Quelqu'un a peut-être battu ton score", "À toi de reprendre la tête, en toute bienveillance."),
    ("🛡️ Pause futée du jour", "Un défi anti-spam pour se changer les idées ?"),
    ("✨ Nouveau défi disponible", "Mêmes grilles pour tout le monde aujourd'hui — montre ce que tu sais faire !"),
  ]

  /// Ask permission (once) and schedule. Returns whether it was actually enabled.
  static func enable() async -> Bool {
    let center = UNUserNotificationCenter.current()
    let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    guard granted else { return false }
    schedule()
    return true
  }

  /// Remove the scheduled reminder (respecting a user who turned it off).
  static func disable() {
    UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [identifier])
  }

  /// (Re)schedule the daily reminder — call on launch when enabled to re-roll the
  /// message and ensure it's registered.
  static func schedule() {
    let center = UNUserNotificationCenter.current()
    center.removePendingNotificationRequests(withIdentifiers: [identifier])
    let pick = messages.randomElement() ?? messages[0]
    let content = UNMutableNotificationContent()
    content.title = pick.title
    content.body = pick.body
    content.sound = .default
    content.userInfo = ["dest": "games"] // tap → open the Games hub (choose a game)
    var comps = DateComponents()
    comps.hour = hour
    comps.minute = 0
    let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
    center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
  }

  /// Hidden Settings debug: fire a reminder-style notification a few seconds
  /// from now, so the whole pipeline (permission → banner → tap routing) can be
  /// verified on-device — background the app to see it arrive, or stay in the
  /// app (the delegate shows banners in foreground too).
  static func scheduleTest(inSeconds seconds: TimeInterval = 5) {
    let pick = messages.randomElement() ?? messages[0]
    let content = UNMutableNotificationContent()
    content.title = pick.title
    content.body = pick.body
    content.sound = .default
    content.userInfo = ["dest": "games"]
    let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
    let request = UNNotificationRequest(identifier: identifier + "_test", content: content, trigger: trigger)
    UNUserNotificationCenter.current().add(request)
  }
}

extension Notification.Name {
  /// Posted when the user taps the daily reminder → the app opens the Games hub.
  static let wcOpenGames = Notification.Name("wc.openGames")
}

/// Routes a reminder tap to the Games hub and shows reminders even in foreground.
final class GameNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
  static let shared = GameNotificationDelegate()

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    completionHandler([.banner, .sound])
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    if response.notification.request.content.userInfo["dest"] as? String == "games" {
      NotificationCenter.default.post(name: .wcOpenGames, object: nil)
    }
    completionHandler()
  }
}
