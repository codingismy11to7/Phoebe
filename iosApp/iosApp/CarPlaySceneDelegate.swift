import CarPlay
import ComposeApp
import ObjectiveC
import UIKit

final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interfaceController: CPInterfaceController?
    private var templateStack: [CPListTemplate] = []

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController
        templateStack.removeAll()
        pushList(parentId: IosCarPlayBridge.shared.rootParentId(), title: "Phoebe", animated: false)
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = nil
        templateStack.removeAll()
    }

    private func pushList(parentId: String, title: String, animated: Bool) {
        IosCarPlayBridge.shared.fetchChildren(parentId: parentId) { [weak self] items in
            guard let self, let interfaceController = self.interfaceController else { return }
            let listItems = items.map { self.makeListItem($0) }
            let section = CPListSection(items: listItems)
            let template = CPListTemplate(title: title, sections: [section])
            template.delegate = self
            objc_setAssociatedObject(
                template,
                &AssociatedKeys.parentId,
                parentId,
                .OBJC_ASSOCIATION_COPY_NONATOMIC
            )
            self.templateStack.append(template)
            if self.templateStack.count == 1 {
                interfaceController.setRootTemplate(template, animated: animated) { _, _ in }
            } else {
                interfaceController.pushTemplate(template, animated: animated) { _, _ in }
            }
        }
    }

    private func makeListItem(_ item: CarPlayBrowseItem) -> CPListItem {
        let listItem = CPListItem(
            text: item.title,
            detailText: item.subtitle,
            image: nil,
            accessoryImage: nil,
            accessoryType: item.isBrowsable ? .disclosureIndicator : .none
        )
        objc_setAssociatedObject(
            listItem,
            &AssociatedKeys.mediaId,
            item.mediaId,
            .OBJC_ASSOCIATION_COPY_NONATOMIC
        )
        objc_setAssociatedObject(
            listItem,
            &AssociatedKeys.isBrowsable,
            item.isBrowsable,
            .OBJC_ASSOCIATION_RETAIN_NONATOMIC
        )
        if let urlString = item.imageUrl, let url = URL(string: urlString) {
            URLSession.shared.dataTask(with: url) { data, _, _ in
                guard let data, let image = UIImage(data: data) else { return }
                let resizedImage = self.resizedImage(image, maxDimension: 96)
                DispatchQueue.main.async {
                    listItem.setImage(resizedImage)
                }
            }.resume()
        }
        return listItem
    }

    private func resizedImage(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let size = image.size
        let largestDimension = max(size.width, size.height)
        guard largestDimension > maxDimension, largestDimension > 0 else { return image }

        let scale = maxDimension / largestDimension
        let targetSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }
}

extension CarPlaySceneDelegate: CPListTemplateDelegate {
    func listTemplate(
        _ listTemplate: CPListTemplate,
        didSelect item: CPListItem,
        completionHandler: @escaping () -> Void
    ) {
        defer { completionHandler() }
        guard
            let mediaId = objc_getAssociatedObject(item, &AssociatedKeys.mediaId) as? String
        else { return }

        let isBrowsable = (objc_getAssociatedObject(item, &AssociatedKeys.isBrowsable) as? Bool) ?? false
        if isBrowsable {
            pushList(parentId: mediaId, title: item.text ?? mediaId, animated: true)
            return
        }

        IosCarPlayBridge.shared.playMediaId(mediaId: mediaId)
        interfaceController?.pushTemplate(CPNowPlayingTemplate.shared, animated: true) { _, _ in }
    }
}

private enum AssociatedKeys {
    static var parentId: UInt8 = 0
    static var mediaId: UInt8 = 0
    static var isBrowsable: UInt8 = 0
}
