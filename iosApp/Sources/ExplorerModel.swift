import CoreLocation
import Foundation
import MapKit
import Observation
@preconcurrency import Shared

/// Value copy of a Kotlin `Flight`. Made once per result change, so scrolling and diffing stay in
/// Swift instead of crossing into Kotlin for every field of every row on every frame.
struct FlightItem: Identifiable, Equatable {
    let id: String
    let origin: String
    let destination: String
    let originCity: String
    let destinationCity: String
    let departureLabel: String
    let departureRowLabel: String
    let cabin: String
    let program: String
    let pointsLabel: String
    let originCoordinate: CLLocationCoordinate2D
    let destinationCoordinate: CLLocationCoordinate2D
    let source: Flight

    init(_ flight: Flight) {
        id = flight.id
        origin = flight.origin
        destination = flight.destination
        originCity = flight.originCity
        destinationCity = flight.destinationCity
        departureLabel = flight.departureLabel
        departureRowLabel = flight.departureRowLabel
        cabin = flight.cabin
        program = flight.program
        pointsLabel = flight.pointsLabel
        originCoordinate = CLLocationCoordinate2D(latitude: flight.originLatitude, longitude: flight.originLongitude)
        destinationCoordinate = CLLocationCoordinate2D(latitude: flight.destinationLatitude, longitude: flight.destinationLongitude)
        source = flight
    }

    var routeLabel: String { "\(origin) to \(destination)" }

    static func == (a: FlightItem, b: FlightItem) -> Bool {
        a.id == b.id && a.pointsLabel == b.pointsLabel && a.departureLabel == b.departureLabel
            && a.cabin == b.cabin && a.program == b.program
    }
}

struct MarkerItem: Identifiable, Equatable {
    let code: String
    let city: String
    let flightCount: Int
    let lowestPoints: Int
    let pointsLabel: String
    let coordinate: CLLocationCoordinate2D
    let source: DestinationMarker

    var id: String { code }

    init(_ marker: DestinationMarker) {
        code = marker.code
        city = marker.city
        flightCount = Int(marker.flightCount)
        lowestPoints = Int(marker.lowestPoints)
        pointsLabel = FormattingKt.formatPoints(points: marker.lowestPoints)
        coordinate = CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)
        source = marker
    }

    var compactPointsLabel: String { FormattingKt.compactPoints(points: Int32(lowestPoints)) }

    static func == (a: MarkerItem, b: MarkerItem) -> Bool {
        a.code == b.code && a.flightCount == b.flightCount && a.lowestPoints == b.lowestPoints
    }
}

struct FilterChipItem: Identifiable, Equatable {
    let id: String
    let label: String
    let source: ActiveFilter

    static func == (a: FilterChipItem, b: FilterChipItem) -> Bool { a.id == b.id && a.label == b.label }
}

enum ContentPhase {
    case loading, error, noFlights, noMatches, results

    init(_ kind: ContentKind) {
        switch kind {
        case .loading: self = .loading
        case .error: self = .error
        case .noFlights: self = .noFlights
        case .noMatches: self = .noMatches
        default: self = .results
        }
    }
}

/// Mirrors the shared `ExplorerController` into SwiftUI. It holds no logic of its own: every action
/// goes to the controller, and every value comes back through `apply`. A property is only written
/// when it changes, because `@Observable` invalidates its readers on any write.
@MainActor
@Observable
final class ExplorerModel {
    private let controller: ExplorerController
    @ObservationIgnored private var subscription: (any Subscription)?
    @ObservationIgnored private var resultsRevision: Int32 = -1
    @ObservationIgnored private var savedRevision: Int32 = -1

    private(set) var phase = ContentPhase.loading
    private(set) var isRefreshing = false
    private(set) var isStale = false
    private(set) var failureMessage: String?
    private(set) var lastUpdatedLabel: String?

    private(set) var results: [FlightItem] = []
    private(set) var markers: [MarkerItem] = []
    private(set) var selectedFlight: FlightItem?
    private(set) var selectedDestination: MarkerItem?
    private(set) var destinationFlights: [FlightItem] = []

    private(set) var saved: [FlightItem] = []
    private(set) var savedIds: Set<String> = []
    private(set) var pendingIds: Set<String> = []
    private(set) var message: UserMessage?

    private(set) var activeFilters: [FilterChipItem] = []
    private(set) var cabin: String?
    private(set) var maxPoints: Int?
    private(set) var selectedPrograms: Set<String> = []
    private(set) var dateStartIso: String?
    private(set) var dateEndIso: String?

    private(set) var cabinOptions: [String] = []
    private(set) var programOptions: [String] = []
    private(set) var firstDateIso: String?
    private(set) var lastDateIso: String?
    private(set) var minPointsOption = 0
    private(set) var maxPointsOption = 0

    /// Text stays here so typing never waits on a round trip. It follows shared state only when that
    /// disagrees, which is how Reset and the chips clear the fields.
    var originText = "" {
        didSet { if originText != oldValue { controller.setOriginText(text: originText) } }
    }
    var destinationText = "" {
        didSet { if destinationText != oldValue { controller.setDestinationText(text: destinationText) } }
    }

    init() {
        let configured = Bundle.main.object(forInfoDictionaryKey: "APIBaseURL") as? String
        let baseUrl = ProcessInfo.processInfo.environment["API_BASE_URL"] ?? configured ?? "http://localhost:8080"
        // Debug QA switch, set from the scheme or a UI test. See the README.
        #if DEBUG
        let failSavedWrites = ProcessInfo.processInfo.environment["FAIL_SAVED_WRITES"] == "1"
        #else
        let failSavedWrites = false
        #endif
        controller = IosExplorerKt.createExplorerController(baseUrl: baseUrl, failSavedWrites: failSavedWrites)
        subscription = controller.watch { [weak self] state in
            MainActor.assumeIsolated { self?.apply(state) }
        }
        controller.start()
    }

    private func apply(_ state: ExplorerState) {
        assign(\.phase, ContentPhase(state.content), changed: phase != ContentPhase(state.content))
        assign(\.isRefreshing, state.isRefreshing, changed: isRefreshing != state.isRefreshing)
        assign(\.isStale, state.isStale, changed: isStale != state.isStale)
        assign(\.failureMessage, state.failureMessage, changed: failureMessage != state.failureMessage)
        assign(\.lastUpdatedLabel, state.lastUpdatedLabel, changed: lastUpdatedLabel != state.lastUpdatedLabel)

        // Only re-map the big list when its revision says it really changed.
        if state.resultsRevision != resultsRevision {
            resultsRevision = state.resultsRevision
            results = state.results.map(FlightItem.init)
        }
        if state.savedRevision != savedRevision {
            savedRevision = state.savedRevision
            saved = state.saved.map(FlightItem.init)
            savedIds = state.savedIds
        }

        let nextMarkers = state.markers.map(MarkerItem.init)
        if nextMarkers != markers { markers = nextMarkers }
        let nextFlight = state.selectedFlight.map(FlightItem.init)
        if nextFlight != selectedFlight { selectedFlight = nextFlight }
        let nextDestination = state.selectedDestination.map(MarkerItem.init)
        if nextDestination != selectedDestination { selectedDestination = nextDestination }
        let nextDestinationFlights = state.destinationFlights.map(FlightItem.init)
        if nextDestinationFlights != destinationFlights { destinationFlights = nextDestinationFlights }

        if pendingIds != state.pendingSaveIds { pendingIds = state.pendingSaveIds }
        if message?.id != state.message?.id { message = state.message }

        let chips = state.activeFilters.map { FilterChipItem(id: $0.id, label: $0.label, source: $0) }
        if chips != activeFilters { activeFilters = chips }

        let query = state.query
        if originText != query.originText { originText = query.originText }
        if destinationText != query.destinationText { destinationText = query.destinationText }
        if cabin != query.cabin { cabin = query.cabin }
        let limit = query.maxPoints?.intValue
        if maxPoints != limit { maxPoints = limit }
        if selectedPrograms != query.programs { selectedPrograms = query.programs }
        if dateStartIso != query.dateStartIso { dateStartIso = query.dateStartIso }
        if dateEndIso != query.dateEndIso { dateEndIso = query.dateEndIso }

        let options = state.options
        if cabinOptions != options.cabins { cabinOptions = options.cabins }
        if programOptions != options.programs { programOptions = options.programs }
        if firstDateIso != options.firstDateIso { firstDateIso = options.firstDateIso }
        if lastDateIso != options.lastDateIso { lastDateIso = options.lastDateIso }
        if minPointsOption != Int(options.minPoints) { minPointsOption = Int(options.minPoints) }
        if maxPointsOption != Int(options.maxPoints) { maxPointsOption = Int(options.maxPoints) }
    }

    private func assign<Value>(_ keyPath: ReferenceWritableKeyPath<ExplorerModel, Value>, _ value: Value, changed: Bool) {
        if changed { self[keyPath: keyPath] = value }
    }

    // Actions

    func refresh() { controller.refresh() }
    func selectFlight(_ id: String?) { controller.selectFlight(id: id) }
    func selectDestination(_ code: String) { controller.selectDestination(code: code) }
    func clearSelection() { controller.clearSelection() }

    func toggleSaved(_ flight: FlightItem) {
        if savedIds.contains(flight.id) { controller.remove(flight: flight.source) } else { controller.save(flight: flight.source) }
    }

    func remove(_ flight: FlightItem) { controller.remove(flight: flight.source) }
    func retryMessage() { controller.retryMessage() }
    func dismissMessage() { controller.dismissMessage() }

    func resetFilters() { controller.resetFilters() }
    func removeFilter(_ filter: FilterChipItem) { controller.removeFilter(filter: filter.source) }
    func setCabin(_ cabin: String?) { controller.setCabin(cabin: cabin) }
    func toggleProgram(_ program: String) { controller.toggleProgram(program: program) }
    func setMaxPoints(_ points: Int) { controller.setMaxPoints(points: KotlinInt(int: Int32(points))) }
    func setDateStart(_ iso: String) { controller.setDateStartIso(iso: iso) }
    func setDateEnd(_ iso: String) { controller.setDateEndIso(iso: iso) }

    /// Non-search filters, for the badge on the filter button.
    var filterCount: Int {
        activeFilters.filter { $0.source.kind != .origin && $0.source.kind != .destination }.count
    }

    var resultSummary: String {
        switch phase {
        case .loading: "Loading flights…"
        case .error, .noFlights: "No flights"
        case .noMatches, .results:
            results.count == 1 ? "1 flight" : "\(FormattingKt.formatPoints(points: Int32(results.count))) flights"
        }
    }

    /// Where each marker's label should sit for a map showing one world `worldWidth` points across.
    func markerPlacements(worldWidth: Double) -> [String: MarkerPlacement] {
        let placements = MarkerLayoutKt.layoutMarkers(markers: markers.map(\.source), worldWidth: worldWidth)
        return Dictionary(uniqueKeysWithValues: placements.map { ($0.code, $0) })
    }

    /// The camera region that frames a route, with the antimeridian already handled in shared code.
    func routeRegion(for flight: FlightItem) -> MKCoordinateRegion {
        let frame = RouteGeometryKt.routeFrame(flight: flight.source)
        let latitude = min(max(frame.centerLatitude, -80), 80)
        let maxSpan = (90 - abs(latitude)) * 2 - 1
        return MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: latitude, longitude: frame.centerLongitude),
            span: MKCoordinateSpan(latitudeDelta: min(frame.latitudeSpan, maxSpan), longitudeDelta: frame.longitudeSpan)
        )
    }
}
