import XCTest

final class KwakStreamUITests: XCTestCase {
    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
        super.tearDown()
    }

    private func preview(in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "previewArea").firstMatch
    }

    private func verifyAspect(_ element: XCUIElement, widthOverHeight expected: CGFloat, label: String) {
        let size = element.frame.size
        let actual = size.width / max(size.height, 1)
        print("[GEOMETRY][\(label)] CGRect=\(element.frame) expected=\(expected) actual=\(actual) accessibilityValue=\(element.value ?? "nil")")
        XCTAssertGreaterThan(size.width, 0)
        XCTAssertGreaterThan(size.height, 0)
        XCTAssertEqual(actual, expected, accuracy: 0.015, "\(label): 실측 미리보기 비율 불일치")
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "\(label)-actual-geometry"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testLandscapePreviewAndControls() {
        XCUIDevice.shared.orientation = .landscapeLeft
        let app = XCUIApplication()
        app.launch()
        let area = preview(in: app)
        XCTAssertTrue(area.waitForExistence(timeout: 15))
        verifyAspect(area, widthOverHeight: 16.0/9.0, label: "landscape")
        let button = app.buttons["switchCamera"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        XCTAssertFalse(area.frame.intersects(button.frame), "조작 버튼이 미리보기와 겹칩니다")
    }

    func testSimulatorSwitchAndZoomDoNotOpenCamera() {
        let app = XCUIApplication()
        app.launch()
        let status = app.staticTexts["cameraStatus"]
        XCTAssertTrue(status.waitForExistence(timeout: 15))
        XCTAssertTrue(status.label.contains("SIMULATOR"))
        let button = app.buttons["switchCamera"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
        let front = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", "[전면]"), object: status)
        XCTAssertEqual(XCTWaiter.wait(for: [front], timeout: 5), .completed, "전면 전환 실패: \(status.label)")
        let frontMenu = app.buttons["넓게 선택됨, 목록"]
        XCTAssertTrue(frontMenu.waitForExistence(timeout: 5))
        frontMenu.tap()
        let normal = app.buttons["일반"]
        XCTAssertTrue(normal.waitForExistence(timeout: 5))
        normal.tap()
        XCTAssertTrue(app.buttons["일반 선택됨, 목록"].waitForExistence(timeout: 5))
        app.buttons["일반 선택됨, 목록"].tap()
        XCTAssertTrue(app.buttons["넓게"].waitForExistence(timeout: 5))
        app.buttons["넓게"].tap()
        XCTAssertTrue(app.buttons["넓게 선택됨, 목록"].waitForExistence(timeout: 5))
        button.tap()
        let back = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", "[후면]"), object: status)
        XCTAssertEqual(XCTWaiter.wait(for: [back], timeout: 5), .completed, "후면 전환 실패: \(status.label)")
        XCTAssertTrue(app.buttons["zoom_1.0"].exists)
        XCTAssertFalse(status.label.contains("E_"))
    }
}
