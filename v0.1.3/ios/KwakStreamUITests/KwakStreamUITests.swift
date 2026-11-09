import XCTest
final class KwakStreamUITests: XCTestCase {
    func testPreviewAreaAspectAcrossRotation() {
        let app = XCUIApplication()
        XCUIDevice.shared.orientation = .portrait
        app.launch()
        let preview = app.otherElements["previewArea"]
        XCTAssertTrue(preview.waitForExistence(timeout: 15))
        let button = app.buttons["switchCamera"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        XCTAssertTrue(preview.frame.intersects(button.frame))
        let portraitCapture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        portraitCapture.name = "portrait-9x16"; portraitCapture.lifetime = .keepAlways; add(portraitCapture)
        let portrait = preview.frame
        XCTAssertGreaterThan(portrait.height, 0)
        XCTAssertEqual(portrait.width / portrait.height, 9.0 / 16.0, accuracy: 0.02)
        XCUIDevice.shared.orientation = .landscapeLeft
        let landscape = expectation(for: NSPredicate { _, _ in
            let frame = preview.frame
            return frame.height > 0 && abs(frame.width / frame.height - 16.0 / 9.0) < 0.02
        }, evaluatedWith: nil)
        wait(for: [landscape], timeout: 10)
        let landscapeCapture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        landscapeCapture.name = "landscape-16x9"; landscapeCapture.lifetime = .keepAlways; add(landscapeCapture)
        XCUIDevice.shared.orientation = .portrait
    }
    func testSimulatorDisplaysCameraUnavailable() {
        let app = XCUIApplication(); app.launch()
        let status = app.staticTexts["cameraStatus"]
        XCTAssertTrue(status.waitForExistence(timeout: 15))
        XCTAssertTrue(status.label.contains("시뮬레이터"))
        XCTAssertTrue(app.buttons["switchCamera"].exists)
    }
}
