import Foundation

// MARK: - API models

struct ListNumberDTO: Codable {
  let phone: String
  let status: String
  let spamScore: Int
  let category: String
  let source: String?
  let updatedAt: String
}

struct ListPatternDTO: Codable {
  let pattern: String
  let status: String
  let category: String
  let source: String
  let name: String?
  let updatedAt: String
}

struct ListResponseDTO: Codable {
  let serverTime: String
  let count: Int
  let next: String?
  let numbers: [ListNumberDTO]
  let patterns: [ListPatternDTO]?
  // Phones to drop from the local cache (healed / deleted upstream).
  let deleted: [String]?
}

/// GET /lists/access — short-lived token gating the list download.
struct ListAccessDTO: Codable {
  let token: String
  let expiresIn: Int
}

struct LookupFrequencyDTO: Codable {
  let last24h: Int
  let last7d: Int
  let last30d: Int
  let last1y: Int
}

/// Dominant community reason ("why did it call?") for a number.
struct TopReasonDTO: Codable {
  let category: String  // telemarketing | scam | robocall | unknown
  let count: Int
  let share: Int  // 0..100 — share of spam reports
}

struct LookupResponseDTO: Codable {
  let phone: String
  let spamScore: Int
  let status: String
  let category: String?
  let source: String?
  let reportCountSpam: Int?
  let reportCountLegit: Int?
  let frequency: LookupFrequencyDTO?
  let topReason: TopReasonDTO?
  let firstReportedAt: String?
  let lastReportedAt: String?
}

/// One number in the "qui montent" trending list.
struct TrendingNumberDTO: Codable, Identifiable {
  let phone: String
  let reportCount: Int
  let last24h: Int
  let velocity: Int
  let spamScore: Int
  let status: String
  let category: String
  let source: String
  let topReason: TopReasonDTO?

  var id: String { phone }
}

struct TrendingResponseDTO: Codable {
  let serverTime: String?
  let window: Int?
  let count: Int?
  let numbers: [TrendingNumberDTO]
}

struct ReportRequestDTO: Codable {
  let phone: String
  let deviceId: String
  let vote: String  // "spam" | "legit"
  let category: String?
  let locale: String
}

/// GET /stats — public reassurance figures shown on the Home banner.
struct StatsResponseDTO: Codable {
  let serverTime: String?
  let coveredNumbers: Int?  // big "numéros couverts" headline
  let communityCount: Int?  // honest count of crowdsourced numbers
  let arcepPatternCount: Int?  // number of ARCEP block ranges
  let lastUpdate: String?  // ISO-8601 of freshest data, or null
  let country: String?  // echoed dial code, or null if global
  let countryNumbers: Int?  // numbers for that country
  let countryArcepPatternCount: Int?  // ARCEP ranges for that country
}

/// POST /game/score — per-game leaderboard (score-based, higher = better).
struct GameScoreRequestDTO: Codable {
  let deviceId: String
  let score: Int
  let waves: Int
  let day: Int
  let game: String  // defense | trace
}

/// One TRACE grid served by the backend (region ids + the unique solution).
struct TraceGrid: Codable {
  let n: Int
  let region: [[Int]]
  let solution: [Int]
}

/// GET /game/puzzle — the day's TRACE sprint (5 grids) or a training grid.
struct TracePuzzleSetDTO: Codable {
  let day: Int?
  let game: String?
  let mode: String?
  let grids: [TraceGrid]
}

struct GameScoreResponseDTO: Codable {
  let players: Int
  let bestScore: Int
  let medianScore: Int
  let yourScore: Int
  let rank: Int
  let topPercent: Int
}

struct LeaderboardEntryDTO: Codable, Identifiable { let rank: Int; let score: Int; let waves: Int; var id: Int { rank } }
struct LeaderboardYouDTO: Codable { let score: Int; let waves: Int; let rank: Int; let topPercent: Int }
struct LeaderboardResponseDTO: Codable {
  let day: Int; let players: Int; let bestScore: Int; let medianScore: Int
  let top: [LeaderboardEntryDTO]; let you: LeaderboardYouDTO?
}
struct HistoryEntryDTO: Codable, Identifiable {
  let day: Int; let score: Int; let waves: Int; let rank: Int; let players: Int; let topPercent: Int
  var id: Int { day }
}
struct HistoryResponseDTO: Codable { let entries: [HistoryEntryDTO] }

struct CommunityResponseDTO: Codable { let total: Int; let week: Int; let goal: Int }

enum APIError: Error {
  case invalidURL
  case http(Int)
  case decoding
}

/// Minimal async HTTP client for the who-called API (URLSession + Codable).
struct WhoCalledAPI {
  /// Short-lived access token gating GET /lists (the download URL is
  /// deliberately temporary — dead a few minutes later if replayed).
  func listsAccess() async throws -> String {
    guard let url = URL(string: "\(AppConstants.apiListsURL)/access") else {
      throw APIError.invalidURL
    }
    let dto: ListAccessDTO = try await get(url)
    return dto.token
  }

  func fetchList(since: String?, country: String? = nil, limit: Int = 5000, token: String) async throws -> ListResponseDTO {
    var components = URLComponents(string: AppConstants.apiListsURL)
    var items = [URLQueryItem(name: "limit", value: String(limit))]
    if let since { items.append(URLQueryItem(name: "since", value: since)) }
    if let country, !country.isEmpty { items.append(URLQueryItem(name: "country", value: country)) }
    items.append(URLQueryItem(name: "token", value: token))
    components?.queryItems = items
    guard let url = components?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  func lookup(_ phone: String) async throws -> LookupResponseDTO {
    guard let url = URL(string: "\(AppConstants.apiLookupURL)/\(phone)") else {
      throw APIError.invalidURL
    }
    return try await get(url)
  }

  /// "Numéros qui montent" — most-reported numbers ramping up recently.
  func trending(windowDays: Int = 7, limit: Int = 25) async throws -> TrendingResponseDTO {
    var components = URLComponents(string: AppConstants.apiTrendingURL)
    components?.queryItems = [
      URLQueryItem(name: "window", value: String(windowDays)),
      URLQueryItem(name: "limit", value: String(limit)),
    ]
    guard let url = components?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// Shared community-shield figures (total + this week + soft weekly goal).
  func community() async throws -> CommunityResponseDTO {
    guard let url = URL(string: AppConstants.apiStatsURL + "/community") else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// Public reassurance stats. `country` (dial code) adds that country's counts.
  func stats(country: String? = nil) async throws -> StatsResponseDTO {
    var components = URLComponents(string: AppConstants.apiStatsURL)
    if let country, !country.isEmpty {
      components?.queryItems = [URLQueryItem(name: "country", value: country)]
    }
    guard let url = components?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// Connectivity probe for the Settings "mode test" (double-tap version).
  /// Hits the root /health endpoint and returns the round-trip latency in ms.
  func ping() async throws -> Int {
    guard let url = URL(string: AppConstants.apiHealthURL) else { throw APIError.invalidURL }
    var request = URLRequest(url: url)
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    let start = Date()
    let (_, response) = try await URLSession.shared.data(for: request)
    try validate(response)
    return Int(Date().timeIntervalSince(start) * 1000)
  }

  func report(_ body: ReportRequestDTO) async throws {
    guard let url = URL(string: AppConstants.apiReportsURL) else { throw APIError.invalidURL }
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = try JSONEncoder().encode(body)
    let (_, response) = try await URLSession.shared.data(for: request)
    try validate(response)
  }

  /// Submit a run for `game` → returns today's anonymous leaderboard position.
  func submitGameScore(score: Int, waves: Int, day: Int, game: String = "defense") async throws -> GameScoreResponseDTO {
    guard let url = URL(string: AppConstants.apiGameScoreURL) else { throw APIError.invalidURL }
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    request.httpBody = try JSONEncoder().encode(
      GameScoreRequestDTO(deviceId: SharedStore.deviceId(), score: score, waves: waves, day: day, game: game))
    let (data, response) = try await URLSession.shared.data(for: request)
    try validate(response)
    do { return try JSONDecoder().decode(GameScoreResponseDTO.self, from: data) }
    catch { throw APIError.decoding }
  }

  /// Anonymous leaderboard for a game + period (day|week|all) + your position.
  func gameLeaderboard(period: String, day: Int, game: String = "defense") async throws -> LeaderboardResponseDTO {
    var c = URLComponents(string: AppConstants.apiGameScoreURL.replacingOccurrences(of: "/score", with: "/leaderboard"))
    c?.queryItems = [
      URLQueryItem(name: "period", value: period),
      URLQueryItem(name: "day", value: String(day)),
      URLQueryItem(name: "game", value: game),
      URLQueryItem(name: "deviceId", value: SharedStore.deviceId()),
    ]
    guard let url = c?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// This device's recent daily results for a game, with per-day rank.
  func gameHistory(days: Int = 14, game: String = "defense") async throws -> HistoryResponseDTO {
    var c = URLComponents(string: AppConstants.apiGameScoreURL.replacingOccurrences(of: "/score", with: "/history"))
    c?.queryItems = [
      URLQueryItem(name: "deviceId", value: SharedStore.deviceId()),
      URLQueryItem(name: "days", value: String(days)),
      URLQueryItem(name: "game", value: game),
    ]
    guard let url = c?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// TRACE: the day's 5-grid sprint, identical for every player.
  func tracePuzzle(day: Int) async throws -> TracePuzzleSetDTO {
    var c = URLComponents(string: AppConstants.apiGamePuzzleURL)
    c?.queryItems = [URLQueryItem(name: "game", value: "trace"), URLQueryItem(name: "day", value: String(day))]
    guard let url = c?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// TRACE: a single unranked training grid of the requested size.
  func traceTraining(size: Int, seed: Int) async throws -> TracePuzzleSetDTO {
    var c = URLComponents(string: AppConstants.apiGamePuzzleURL)
    c?.queryItems = [
      URLQueryItem(name: "game", value: "trace"),
      URLQueryItem(name: "mode", value: "training"),
      URLQueryItem(name: "size", value: String(size)),
      URLQueryItem(name: "seed", value: String(seed)),
    ]
    guard let url = c?.url else { throw APIError.invalidURL }
    return try await get(url)
  }

  /// RGPD: erase all reports made from this device.
  func eraseDevice(_ deviceId: String) async throws {
    let encoded = deviceId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? deviceId
    guard let url = URL(string: "\(AppConstants.apiBaseURL)/privacy/device/\(encoded)") else {
      throw APIError.invalidURL
    }
    var request = URLRequest(url: url)
    request.httpMethod = "DELETE"
    let (_, response) = try await URLSession.shared.data(for: request)
    try validate(response)
  }

  private func get<T: Decodable>(_ url: URL) async throws -> T {
    var request = URLRequest(url: url)
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    let (data, response) = try await URLSession.shared.data(for: request)
    try validate(response)
    do {
      return try JSONDecoder().decode(T.self, from: data)
    } catch {
      throw APIError.decoding
    }
  }

  private func validate(_ response: URLResponse) throws {
    guard let http = response as? HTTPURLResponse else { return }
    guard (200..<300).contains(http.statusCode) else { throw APIError.http(http.statusCode) }
  }
}
