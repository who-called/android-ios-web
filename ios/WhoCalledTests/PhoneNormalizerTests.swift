import XCTest

@testable import WhoCalled

final class PhoneNormalizerTests: XCTestCase {
  func testFrenchNationalToE164() {
    XCTAssertEqual(PhoneNormalizer.normalize("0612345678"), "33612345678")
    XCTAssertEqual(PhoneNormalizer.normalize("+33 6 12 34 56 78"), "33612345678")
    XCTAssertEqual(PhoneNormalizer.normalize("0033612345678"), "33612345678")
  }

  func testInvalidReturnsNil() {
    XCTAssertNil(PhoneNormalizer.normalize("abc"))
    XCTAssertNil(PhoneNormalizer.normalize(""))
    XCTAssertNil(PhoneNormalizer.normalize(nil))
  }

  func testScoredNumberInt64() {
    let n = ScoredNumber(phone: "33612345678", status: "block", spamScore: 90, category: "scam")
    XCTAssertEqual(n.phoneInt64, 33_612_345_678)
  }
}
