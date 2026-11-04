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

    /// Writes the UI tree next to the screenshots, for when an element can't be found.
    private func dumpTree(_ name: String) {
        let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? ""
        guard !path.isEmpty else { return }
        let url = URL(fileURLWithPath: path).appendingPathComponent("\(name).txt")
        try? app.debugDescription.write(to: url, atomically: true, encoding: .utf8)
    }

    private func any(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier]
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
        let marker = any("marker-JNB")
        let found = marker.waitForExistence(timeout: 10)
        snapshot("07-map")
        dumpTree("07-map")
        XCTAssertTrue(found)
        marker.tap()
        XCTAssertTrue(app.descendants(matching: .any)["destination-panel"].waitForExistence(timeout: 3))
        snapshot("08-destination")
        app.descendants(matching: .any)["destination-panel"].staticTexts.element(boundBy: 2).tap()
        XCTAssertTrue(app.descendants(matching: .any)["flight-card"].waitForExistence(timeout: 3))
        snapshot("09-map-flight")
    }

    func testMapPansAndZooms() {
        launch()
        waitForCount("1,000 flights")
        openMap()
        let marker = any("marker-JNB")
        XCTAssertTrue(marker.waitForExistence(timeout: 10))
        let start = marker.frame
        let map = app.maps.element

        // A short, measured drag: a swipe flings the marker clean off the screen.
        let from = map.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55))
        from.press(forDuration: 0.1, thenDragTo: from.withOffset(CGVector(dx: 60, dy: 0)))
        snapshot("07a-after-pan")
        dumpTree("07a-after-pan")
        XCTAssertGreaterThan(abs(marker.frame.minX - start.minX), 20, "panning should move the map under the markers")

        map.pinch(withScale: 0.3, velocity: -2)
        map.pinch(withScale: 0.3, velocity: -2)
        snapshot("07b-zoomed-out")
        dumpTree("07b-zoomed-out")
    }

    func testNarrowSearchBringsItsMarkersIntoView() {
        launch()
        waitForCount("1,000 flights")
        openMap()
        XCTAssertTrue(any("marker-JNB").waitForExistence(timeout: 10))
        XCTAssertFalse(any("marker-SYD").exists, "Sydney starts off screen, which is the point of this test")

        let destination = app.textFields["destination-field"]
        destination.tap()
        destination.typeText("Sydney")
        XCTAssertTrue(any("marker-SYD").waitForExistence(timeout: 5), "the camera should move to the only result")
        snapshot("07c-sydney")
    }

    func testSavingAppearsInSavedAndRemoveTakesItAway() {
        launch()
        waitForCount("1,000 flights")
        any("results-list").cells.element(boundBy: 1).tap()
        let saveButton = app.buttons["card-save"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        saveButton.tap()
        let becameRemove = expectation(for: NSPredicate(format: "label == 'Remove'"), evaluatedWith: saveButton)
        wait(for: [becameRemove], timeout: 3)

        app.tabBars.buttons["Saved"].tap()
        // Found by content, not by count, so the test doesn't depend on what an earlier run saved.
        let savedRow = any("saved-list").cells.containing(.staticText, identifier: "Chicago to Dublin").element
        XCTAssertTrue(savedRow.waitForExistence(timeout: 3))
        snapshot("10-saved")

        savedRow.tap()
        app.buttons["card-save"].tap()
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: savedRow)
        wait(for: [gone], timeout: 3)
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
