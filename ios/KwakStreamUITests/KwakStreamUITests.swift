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

    func testPreviewAreaAspectAcrossRotation() {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launch()
        let area = preview(in: app)
        XCTAssertTrue(area.waitForExistence(timeout: 15))
        let button = app.buttons["switchCamera"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        verifyAspect(area, widthOverHeight: 9.0/16.0, label: "portrait")
        XCTAssertTrue(area.frame.intersects(button.frame))

        XCUIDevice.shared.orientation = .landscapeLeft
        let rotated = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let frame = area.frame
            guard frame.width > 0 && frame.height > 0 else { return false }
            let ratio = frame.width / frame.height
            print("[ROTATION] device=\(XCUIDevice.shared.orientation.rawValue) frame=\(frame) ratio=\(ratio)")
            return ratio > 1.0 && abs(ratio - 16.0/9.0) < 0.015
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [rotated], timeout: 15), .completed,
                       "가로 전환 실패: 방향/접근성 측정 영역/안전영역 확인 필요")
        verifyAspect(area, widthOverHeight: 16.0/9.0, label: "landscape")
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
        button.tap()
        let back = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", "[후면]"), object: status)
        XCTAssertEqual(XCTWaiter.wait(for: [back], timeout: 5), .completed, "후면 전환 실패: \(status.label)")
        XCTAssertTrue(app.buttons["zoom_1.0"].exists)
        XCTAssertFalse(status.label.contains("E_"))
    }
}
