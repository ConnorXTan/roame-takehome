import XCTest

/// Drives the real app against the local API, which must be running: `./gradlew :server:run`.
/// Set SCREENSHOT_DIR in the test environment to also write a PNG at each named step.
/// The failure-mode tests skip themselves unless started by scripts/ios-failure-tests.sh.
final class FlightExplorerUITests: XCTestCase {
    private var app: XCUIApplication!

    /// A database per test, so no test depends on what an earlier one cached or saved.
    private let databaseName = "uitest-\(UUID().uuidString).db"

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    private func launch(environment: [String: String] = [:]) {
        app.launchEnvironment = environment.merging(["FLIGHT_DB_NAME": databaseName]) { current, _ in current }
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

    func testNewResultsStartAtTheTop() {
        launch()
        waitForCount("1,000 flights")
        let firstRow = any("results-list").cells.element(boundBy: 0)
        let firstRowTop = firstRow.frame.minY
        any("results-list").swipeUp()
        any("results-list").swipeUp()
        snapshot("02b-scrolled")

        let origin = app.textFields["origin-field"]
        origin.tap()
        origin.typeText("Seattle")
        XCTAssertNotEqual(resultCount.label, "1,000 flights")
        app.buttons["reset-filters"].tap()
        waitForCount("1,000 flights")

        XCTAssertTrue(app.staticTexts["Toronto to Barcelona"].waitForExistence(timeout: 3), "the list should be back at the top")
        XCTAssertEqual(firstRow.frame.minY, firstRowTop, accuracy: 2)
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

    /// Opens a compact date picker, steps back to February, and taps a day in its calendar.
    private func pickDay(_ picker: String, label: String) {
        any(picker).buttons.firstMatch.tap()
        let previous = app.buttons["DatePicker.PreviousMonth"]
        XCTAssertTrue(previous.waitForExistence(timeout: 4))
        let day = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
        for _ in 0..<3 where !day.exists {
            previous.tap()
            _ = day.waitForExistence(timeout: 1)
        }
        XCTAssertTrue(day.exists, "calendar should show \(label)")
        day.tap()
        app.buttons["PopoverDismissRegion"].firstMatch.tap()
    }

    /// Flight dates have no time zone, so a device east or west of UTC must not shift them a day.
    func testDateRangeIncludesBothBoundariesInAnyTimeZone() {
        for zone in ["America/Los_Angeles", "Pacific/Auckland"] {
            launch(environment: ["TZ": zone])
            waitForCount("1,000 flights")
            app.buttons["filters-button"].tap()
            pickDay("date-start", label: "February 3")
            pickDay("date-end", label: "February 5")
            snapshot("03b-dates-\(zone.split(separator: "/").last ?? "")")
            app.buttons["show-results"].tap()

            XCTAssertTrue(app.buttons["Remove Feb 3 – Feb 5"].waitForExistence(timeout: 3), "wrong dates in \(zone)")
            waitForCount("41 flights")
            app.buttons["reset-filters"].tap()
            waitForCount("1,000 flights")
            app.terminate()
        }
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

        // Zooming in spreads the airports apart, so two neighbours end up further from each other.
        let athens = any("marker-ATH")
        let rome = any("marker-FCO")
        XCTAssertTrue(athens.waitForExistence(timeout: 3) && rome.exists)
        let before = abs(athens.frame.midX - rome.frame.midX)
        map.pinch(withScale: 2.5, velocity: 2)
        snapshot("07b-zoomed-in")
        let after = expectation(
            for: NSPredicate { _, _ in abs(athens.frame.midX - rome.frame.midX) > before * 1.3 },
            evaluatedWith: nil
        )
        wait(for: [after], timeout: 5)
    }

    func testASearchMadeBeforeOpeningTheMapIsFramed() {
        launch()
        waitForCount("1,000 flights")
        let destination = app.textFields["destination-field"]
        destination.tap()
        destination.typeText("Sydney")
        XCTAssertNotEqual(resultCount.label, "1,000 flights")
        openMap()
        XCTAssertTrue(any("marker-SYD").waitForExistence(timeout: 8), "Sydney starts off screen unless the camera moves")
    }

    func testRoutesOverTheAntimeridianCrossThePacific() {
        launch()
        waitForCount("1,000 flights")
        let origin = app.textFields["origin-field"]
        origin.tap()
        origin.typeText("yvr")
        let destination = app.textFields["destination-field"]
        destination.tap()
        destination.typeText("sydney")
        waitForCount("7 flights")
        any("results-list").cells.element(boundBy: 0).tap()
        app.buttons["show-on-map"].tap()
        XCTAssertTrue(any("marker-SYD").waitForExistence(timeout: 8), "the camera should frame the route")
        RunLoop.current.run(until: Date().addingTimeInterval(2))
        snapshot("16-antimeridian")
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

        // A search that excludes the flight must not hide it from Saved.
        let origin = app.textFields["origin-field"]
        origin.tap()
        origin.typeText("nowhere")
        waitForCount("0 flights")

        app.tabBars.buttons["Saved"].tap()
        XCTAssertTrue(app.staticTexts["saved-count"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["saved-count"].label, "1 flight, available offline")
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

    // MARK: Offline and failure handling

    private let noServer = "http://localhost:9"
    private let emptyServer = "http://localhost:8091"
    private let brokenServer = "http://localhost:8092"
    private let malformedServer = "http://localhost:8093"
    private let lateServer = "http://localhost:8094"

    private func requireFailureServers() throws {
        try XCTSkipUnless(
            ProcessInfo.processInfo.environment["FAILURE_SERVERS"] == "1",
            "Needs the servers started by scripts/ios-failure-tests.sh"
        )
    }

    private func saveSecondFlight() {
        any("results-list").cells.element(boundBy: 1).tap()
        let saveButton = app.buttons["card-save"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        saveButton.tap()
        let saved = expectation(for: NSPredicate(format: "label == 'Remove'"), evaluatedWith: saveButton)
        wait(for: [saved], timeout: 3)
    }

    /// The brief's headline case: reopen with no API, and everything still works from the device.
    func testCachedResultsAndSavedFlightsWorkAfterReopeningOffline() {
        launch()
        waitForCount("1,000 flights")
        saveSecondFlight()
        app.terminate()

        launch(environment: ["API_BASE_URL": noServer])
        waitForCount("1,000 flights")
        XCTAssertTrue(any("stale-banner").waitForExistence(timeout: 10), "cached data should say it is stale")
        snapshot("12-offline")

        let destination = app.textFields["destination-field"]
        destination.tap()
        destination.typeText("Dublin")
        waitForCount("29 flights")
        app.buttons["reset-filters"].tap()
        waitForCount("1,000 flights")

        app.tabBars.buttons["Saved"].tap()
        let savedRow = any("saved-list").cells.containing(.staticText, identifier: "Chicago to Dublin").element
        XCTAssertTrue(savedRow.waitForExistence(timeout: 3))
        savedRow.tap()
        app.buttons["card-save"].tap()
        XCTAssertTrue(app.staticTexts["No saved flights yet"].waitForExistence(timeout: 3), "removing should work offline")
    }

    func testNoCacheAndNoServerIsAnErrorThatRetryRecoversFrom() throws {
        try requireFailureServers()
        launch(environment: ["API_BASE_URL": lateServer])
        XCTAssertTrue(app.staticTexts["Couldn't load flights"].waitForExistence(timeout: 10))
        snapshot("13-error")

        // Tells the script the error has been seen, so it can start the server this test points at.
        let directory = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? "/tmp"
        FileManager.default.createFile(atPath: "\(directory)/start-late-server", contents: nil)

        let retry = app.buttons["state-action"]
        let deadline = Date().addingTimeInterval(60)
        while resultCount.label != "1,000 flights", Date() < deadline {
            if retry.exists { retry.tap() }
            RunLoop.current.run(until: Date().addingTimeInterval(1))
        }
        XCTAssertEqual(resultCount.label, "1,000 flights", "Try again should recover once the server is back")
    }

    func testASuccessfulEmptyResponseIsNotAnError() throws {
        try requireFailureServers()
        launch(environment: ["API_BASE_URL": emptyServer])
        XCTAssertTrue(app.staticTexts["No flights available"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Couldn't load flights"].exists)
        XCTAssertFalse(any("stale-banner").exists)
        snapshot("14-empty")
    }

    func testAServerErrorWithNoCacheIsAnError() throws {
        try requireFailureServers()
        launch(environment: ["API_BASE_URL": brokenServer])
        XCTAssertTrue(app.staticTexts["Couldn't load flights"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["The flight service is having trouble. Please try again."].exists)
    }

    func testAMalformedResponseWithNoCacheIsAnError() throws {
        try requireFailureServers()
        launch(environment: ["API_BASE_URL": malformedServer])
        XCTAssertTrue(app.staticTexts["Couldn't load flights"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["The flight service sent data we couldn't read. Please try again."].exists)
    }

    func testBadResponsesLeaveCachedFlightsInPlace() throws {
        try requireFailureServers()
        launch()
        waitForCount("1,000 flights")
        app.terminate()

        for server in [brokenServer, malformedServer] {
            launch(environment: ["API_BASE_URL": server])
            waitForCount("1,000 flights")
            XCTAssertTrue(any("stale-banner").waitForExistence(timeout: 10), "\(server) should mark the data stale")
            app.terminate()
        }
    }

    func testSavedFlightsSurviveBeingDroppedFromTheApi() throws {
        try requireFailureServers()
        launch()
        waitForCount("1,000 flights")
        saveSecondFlight()
        app.terminate()

        launch(environment: ["API_BASE_URL": emptyServer])
        XCTAssertTrue(app.staticTexts["No flights available"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Saved"].tap()
        let savedRow = any("saved-list").cells.containing(.staticText, identifier: "Chicago to Dublin").element
        XCTAssertTrue(savedRow.waitForExistence(timeout: 3), "the API no longer has this flight, but it was saved")
        snapshot("15-saved-after-empty")
    }
}
