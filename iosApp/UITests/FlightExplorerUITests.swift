import XCTest

/// Drives the real app against the local API, which must be running: `./gradlew :server:run`.
/// Set SCREENSHOT_DIR in the test environment to also write a PNG at each named step.
final class FlightExplorerUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func launch(environment: [String: String] = [:]) {
        app.launchEnvironment = environment
        app.launch()
        XCTAssertTrue(resultCount.waitForExistence(timeout: 15))
    }

    private var resultCount: XCUIElement { app.staticTexts["result-count"] }

    private func snapshot(_ name: String) {
        let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? ""
        guard !path.isEmpty else { return }
        let url = URL(fileURLWithPath: path).appendingPathComponent("\(name).png")
        try? XCUIScreen.main.screenshot().pngRepresentation.write(to: url)
    }

    private func waitForCount(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let predicate = NSPredicate(format: "label == %@", text)
        let found = expectation(for: predicate, evaluatedWith: resultCount)
        wait(for: [found], timeout: 8)
    }

    private func openMap() {
        app.segmentedControls["mode-picker"].buttons.element(boundBy: 1).tap()
    }

    private func openList() {
        app.segmentedControls["mode-picker"].buttons.element(boundBy: 0).tap()
    }

    func testSearchNarrowsResultsAndResetRestoresThem() {
        launch()
        waitForCount("1,000 flights")
        snapshot("01-list")

        let origin = app.textFields["origin-field"]
        origin.tap()
        origin.typeText("  jfK ")
        let destination = app.textFields["destination-field"]
        destination.tap()
        destination.typeText("tokyo")
        XCTAssertNotEqual(resultCount.label, "1,000 flights")
        snapshot("02-search")

        app.buttons["reset-filters"].tap()
        waitForCount("1,000 flights")
        XCTAssertEqual(origin.value as? String, "From · City or airport", "Reset should clear the field")
    }

    func testFiltersCombineAndShowAsChips() {
        launch()
        waitForCount("1,000 flights")
        app.buttons["filters-button"].tap()
        app.buttons["chip-Business"].tap()
        app.buttons["chip-Aeroplan"].tap()
        app.buttons["chip-Avios"].tap()
        snapshot("03-filters")
        app.buttons["show-results"].tap()

        XCTAssertTrue(app.buttons["Remove Business"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["Remove Aeroplan"].exists)
        XCTAssertTrue(app.buttons["Remove Avios"].exists)
        snapshot("04-chips")

        app.buttons["Remove Business"].tap()
        XCTAssertFalse(app.buttons["Remove Business"].waitForExistence(timeout: 1))
        XCTAssertTrue(app.buttons["Remove Avios"].exists)
        app.buttons["reset-filters"].tap()
        waitForCount("1,000 flights")
    }

    func testSelectionCarriesFromListToMapAndClearsWhenFiltered() {
        launch()
        waitForCount("1,000 flights")
        app.descendants(matching: .any)["results-list"].cells.element(boundBy: 0).tap()
        XCTAssertTrue(app.descendants(matching: .any)["flight-card"].waitForExistence(timeout: 3))
        snapshot("05-selected-list")

        app.buttons["show-on-map"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["flight-card"].waitForExistence(timeout: 3), "selection should survive the switch")
        snapshot("06-selected-map")

        let origin = app.textFields["origin-field"]
        origin.tap()
        origin.typeText("nowhere")
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.descendants(matching: .any)["flight-card"])
        wait(for: [gone], timeout: 5)
        app.buttons["reset-filters"].tap()
        waitForCount("1,000 flights")
    }

    func testMarkerOpensItsFlights() {
        launch()
        waitForCount("1,000 flights")
        openMap()
        let marker = app.buttons["marker-GRU"]
        XCTAssertTrue(marker.waitForExistence(timeout: 10))
        snapshot("07-map")
        marker.tap()
        XCTAssertTrue(app.descendants(matching: .any)["destination-panel"].waitForExistence(timeout: 3))
        snapshot("08-destination")
        app.descendants(matching: .any)["destination-panel"].staticTexts.element(boundBy: 2).tap()
        XCTAssertTrue(app.descendants(matching: .any)["flight-card"].waitForExistence(timeout: 3))
        snapshot("09-map-flight")
    }

    func testSavingAppearsInSavedAndRemoveTakesItAway() {
        launch()
        waitForCount("1,000 flights")
        app.descendants(matching: .any)["results-list"].cells.element(boundBy: 1).tap()
        let saveButton = app.buttons["card-save"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        saveButton.tap()
        XCTAssertTrue(app.buttons["Remove"].waitForExistence(timeout: 3) || saveButton.label == "Remove")

        app.tabBars.buttons["Saved"].tap()
        XCTAssertTrue(app.staticTexts["saved-count"].waitForExistence(timeout: 3))
        snapshot("10-saved")
        XCTAssertTrue(app.staticTexts["saved-count"].label.hasPrefix("1 flight"))

        app.descendants(matching: .any)["saved-list"].cells.element(boundBy: 0).tap()
        app.buttons["card-save"].tap()
        XCTAssertTrue(app.staticTexts["No saved flights yet"].waitForExistence(timeout: 3))
    }

    func testFailedSaveStaysUnsavedAndSaysSo() {
        launch(environment: ["FAIL_SAVED_WRITES": "1"])
        waitForCount("1,000 flights")
        app.descendants(matching: .any)["results-list"].cells.element(boundBy: 2).tap()
        let saveButton = app.buttons["card-save"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        saveButton.tap()
        XCTAssertTrue(app.descendants(matching: .any)["message-toast"].waitForExistence(timeout: 3))
        snapshot("11-save-failed")
        XCTAssertEqual(saveButton.label, "Save", "a failed save must not look saved")
    }
}
