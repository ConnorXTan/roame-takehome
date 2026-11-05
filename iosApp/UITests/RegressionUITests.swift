import XCTest

/// Bugs found by /qa on 2026-11-05. Needs the API, and the slow server from scripts/ios-failure-tests.sh.
final class RegressionUITests: XCTestCase {
    private var app: XCUIApplication!
    private let databaseName = "uitest-\(UUID().uuidString).db"

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment = ["FLIGHT_DB_NAME": databaseName]
    }

    private var resultCount: XCUIElement { app.staticTexts["result-count"] }

    private func waitForCount(_ text: String) {
        let found = expectation(for: NSPredicate(format: "label == %@", text), evaluatedWith: resultCount)
        wait(for: [found], timeout: 10)
    }

    // Regression: ISSUE-002. Seven fixed-height skeleton rows overflowed the screen, which pushed the
    // search header up under the status bar for as long as the first load took.
    func testTheHeaderStaysPutWhileLoading() throws {
        try XCTSkipUnless(
            ProcessInfo.processInfo.environment["FAILURE_SERVERS"] == "1",
            "Needs the slow server started by scripts/ios-failure-tests.sh"
        )
        app.launchEnvironment["API_BASE_URL"] = "http://localhost:8095"
        app.launch()
        waitForCount("Loading flights…")
        let origin = app.textFields["origin-field"]
        let whileLoading = origin.frame.minY
        waitForCount("1,000 flights")
        XCTAssertEqual(origin.frame.minY, whileLoading, accuracy: 1, "the header moved when results arrived")
    }

    // Regression: ISSUE-001. At accessibility text sizes the row kept its side-by-side layout and had
    // no room left, so codes and prices wrapped a character or two at a time.
    func testRowsStayReadableAtTheLargestTextSize() {
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        waitForCount("1,000 flights")

        for text in ["YYZ", "BCN", "16,000"] {
            let label = app.staticTexts[text].firstMatch
            XCTAssertTrue(label.waitForExistence(timeout: 3))
            XCTAssertGreaterThan(label.frame.width, label.frame.height, "\(text) has wrapped onto several lines")
        }

        let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? ""
        func snapshot(_ name: String) {
            guard !path.isEmpty else { return }
            let url = URL(fileURLWithPath: path).appendingPathComponent("\(name).png")
            try? XCUIScreen.main.screenshot().pngRepresentation.write(to: url)
        }
        snapshot("a11y-list")

        // The card has to stay usable at this size too, or the flight can't be saved.
        let origin = app.textFields["origin-field"]
        let headerTop = origin.frame.minY
        app.staticTexts["YYZ"].firstMatch.tap()
        let save = app.buttons["card-save"]
        XCTAssertTrue(save.waitForExistence(timeout: 3))
        XCTAssertTrue(save.isHittable, "Save should be reachable on the card")
        XCTAssertEqual(origin.frame.minY, headerTop, accuracy: 1, "a tall card must not push the header up")
        // A truncated "1…" keeps its full accessibility label, so its width is what gives it away.
        let cardPrice = app.descendants(matching: .any)["flight-card"].staticTexts["16,000"]
        XCTAssertGreaterThan(cardPrice.frame.width, 100, "the price on the card has been truncated")
        snapshot("a11y-card")
        app.buttons["close-card"].tap()

        app.buttons["filters-button"].tap()
        XCTAssertTrue(app.buttons["show-results"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["show-results"].isHittable, "Show results should be reachable")
        let dateLabel = app.staticTexts["From"].firstMatch
        XCTAssertTrue(dateLabel.exists)
        XCTAssertGreaterThan(dateLabel.frame.width, dateLabel.frame.height, "the date label has wrapped")
        snapshot("a11y-filters")
    }

    /// The real fixture, read straight from the repository, as an oracle independent of the app.
    private func pointsInFixture() throws -> [Int] {
        let fixture = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("fixtures/flights.json")
        let json = try JSONSerialization.jsonObject(with: Data(contentsOf: fixture)) as? [String: Any]
        let flights = try XCTUnwrap(json?["flights"] as? [[String: Any]])
        return flights.compactMap { $0["points"] as? Int }
    }

    // The points limit has to include flights priced exactly at it, through the real slider.
    func testTheMaximumPointsSliderIncludesTheLimit() throws {
        let points = try pointsInFixture()
        app.launch()
        waitForCount("1,000 flights")
        app.buttons["filters-button"].tap()

        let slider = app.sliders["points-slider"]
        XCTAssertTrue(slider.waitForExistence(timeout: 3))
        let showButton = app.buttons["show-results"]
        let limitLabel = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Up to '")).firstMatch

        for position in [0.0, 0.1, 0.25, 0.5, 0.8] {
            slider.adjust(toNormalizedSliderPosition: CGFloat(position))
            XCTAssertTrue(limitLabel.waitForExistence(timeout: 3))
            let limit = try XCTUnwrap(Int(limitLabel.label.filter(\.isNumber)))
            let expected = points.filter { $0 <= limit }.count
            let shown = expected == 1 ? "Show 1 flight" : "Show \(expected) flights"
            let matches = expectation(for: NSPredicate(format: "label == %@", shown), evaluatedWith: showButton)
            wait(for: [matches], timeout: 3)
        }

        slider.adjust(toNormalizedSliderPosition: 1)
        XCTAssertTrue(app.staticTexts["No limit"].waitForExistence(timeout: 3), "the far end should lift the limit")
        XCTAssertEqual(showButton.label, "Show 1000 flights")
    }
}
