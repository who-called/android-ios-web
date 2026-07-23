import IdentityLookup

/// who-called SMS shield (iOS Message Filter Extension).
///
/// iOS routes SMS from unknown senders to this extension when the user has
/// enabled "Filtrer les expéditeurs inconnus" → who-called in Settings. We decide
/// offline from the same shared list as the call blocker: a sender whose number
/// is `block` is filtered to the Junk folder. Inconclusive cases are allowed
/// (we deliberately do NOT defer to a network query — 100% offline, no content
/// ever leaves the device).
final class MessageFilterExtension: ILMessageFilterExtension {}

extension MessageFilterExtension: ILMessageFilterQueryHandling {
  func handle(
    _ queryRequest: ILMessageFilterQueryRequest,
    context: ILMessageFilterExtensionContext,
    completion: @escaping (ILMessageFilterQueryResponse) -> Void
  ) {
    let response = ILMessageFilterQueryResponse()
    response.action = MessageFilterService().action(for: queryRequest.sender)
    completion(response)
  }
}
