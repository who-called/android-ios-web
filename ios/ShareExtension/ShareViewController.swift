import MobileCoreServices
import Social
import UIKit
import UniformTypeIdentifiers

/// Share Extension: lets the user share a phone number or an SMS body from any
/// app (Messages, Phone, Safari…) into Who Called to report it. No SMS
/// permission, no passive reading — the user explicitly shares. We extract a
/// phone-like candidate and hand off to the host app via a custom URL
/// (whocalled://report?phone=…), where the Report tab is pre-filled.
final class ShareViewController: UIViewController {

  override func viewDidLoad() {
    super.viewDidLoad()
    extractSharedText { [weak self] text in
      let phone = PhoneNormalizer.extractFromText(text) ?? text?.trimmingCharacters(in: .whitespacesAndNewlines)
      self?.openHostApp(phone: phone)
    }
  }

  /// Pull the first plain-text / URL item out of the share context.
  private func extractSharedText(completion: @escaping (String?) -> Void) {
    guard
      let item = extensionContext?.inputItems.first as? NSExtensionItem,
      let providers = item.attachments
    else {
      completion(nil)
      return
    }

    let textType = UTType.plainText.identifier
    let urlType = UTType.url.identifier

    for provider in providers {
      if provider.hasItemConformingToTypeIdentifier(textType) {
        provider.loadItem(forTypeIdentifier: textType, options: nil) { value, _ in
          DispatchQueue.main.async { completion(value as? String) }
        }
        return
      }
      if provider.hasItemConformingToTypeIdentifier(urlType) {
        provider.loadItem(forTypeIdentifier: urlType, options: nil) { value, _ in
          DispatchQueue.main.async { completion((value as? URL)?.absoluteString) }
        }
        return
      }
    }
    completion(nil)
  }

  /// Open the host app with the extracted number, then finish the extension.
  private func openHostApp(phone: String?) {
    defer { extensionContext?.completeRequest(returningItems: nil) }
    guard
      let phone,
      !phone.isEmpty,
      let encoded = phone.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
      let url = URL(string: "whocalled://report?phone=\(encoded)")
    else { return }

    // Walk the responder chain to call openURL: from an app extension.
    var responder: UIResponder? = self
    let selector = sel_registerName("openURL:")
    while let r = responder {
      if r.responds(to: selector) {
        r.perform(selector, with: url)
        break
      }
      responder = r.next
    }
  }
}
