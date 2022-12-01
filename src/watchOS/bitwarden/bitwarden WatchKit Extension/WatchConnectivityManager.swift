import Combine
import Foundation
import WatchConnectivity

struct NotificationMessage: Identifiable {
    let id = UUID()
    let text: String
}

struct WatchConnectivityMessage {
    var state: BWState?
}

final class WatchConnectivityManager: NSObject, ObservableObject {
    static let shared = WatchConnectivityManager()
    @Published var notificationMessage: NotificationMessage? = nil
    
    let watchConnectivitySubject = CurrentValueSubject<WatchConnectivityMessage, Error>(WatchConnectivityMessage(state: nil))

    private let kMessageKey = "message"
    private let kCipherDataKey = "watchDto"
    
    private override init() {
        super.init()
        
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
    }
    
    var isSessionActivated: Bool {
        return WCSession.default.isCompanionAppInstalled && WCSession.default.activationState == .activated
    }
        
    func send(_ message: String) {
        guard WCSession.default.activationState == .activated else {
          return
        }
        
        guard WCSession.default.isCompanionAppInstalled else {
            return
        }
        
        WCSession.default.sendMessage([kMessageKey : message], replyHandler: nil) { error in
            Log.e("Cannot send message: \(String(describing: error))")
        }
    }
}

extension WatchConnectivityManager: WCSessionDelegate {
    func session(_ session: WCSession, didReceiveMessage message: [String : Any]) {
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any], replyHandler: @escaping ([String : Any]) -> Void) {
    }
    
    func session(_ session: WCSession,
                 activationDidCompleteWith activationState: WCSessionActivationState,
                 error: Error?) {
    }
    
    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String : Any]) {
        // in order for the delivery to be faster the time is added to the key to make each application context update have a different key
        // and update faster
        let watchDtoKey = applicationContext.keys.first { k in
            k.starts(with: kCipherDataKey)
        }
        
        guard let dtoKey = watchDtoKey, let serializedDto = applicationContext[dtoKey] as? String else {
            return
        }
        
        do {
            guard let json = try! JSONSerialization.jsonObject(with: serializedDto.data(using: .utf8)!, options: [.fragmentsAllowed]) as? String else {
                return
            }
            
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .upperToLowerCamelCase
            let watchDTO = try decoder.decode(WatchDTO.self, from: json.data(using: .utf8)!)
            
            let previousUserId = StateService.shared.getUser()?.id
            
            if previousUserId != watchDTO.userData?.id {
                self.watchConnectivitySubject.send(WatchConnectivityMessage(state: .syncing))
            }
            
            StateService.shared.currentState = watchDTO.state
            StateService.shared.setUser(user: watchDTO.userData)
//            StateService.shared.setVaultTimeout(watchDTO.settingsData?.vaultTimeoutInMinutes, watchDTO.settingsData?.vaultTimeoutAction ?? .lock)
            EnvironmentService.shared.baseUrl = watchDTO.environmentData?.base
            EnvironmentService.shared.setIconsUrl(url: watchDTO.environmentData?.icons)
            
            if watchDTO.state.isDestructive {
                CipherService.shared.deleteAll(nil) {
                    self.watchConnectivitySubject.send(WatchConnectivityMessage(state: nil))
                }
            }
            
            if watchDTO.state == .valid, var ciphers = watchDTO.ciphers {
                // we need to track the to which user the ciphers belong to, so we add the user here to all ciphers
                // note: it's not being sent directly from the phone to increase performance on the communication
                ciphers.indices.forEach { i in
                    ciphers[i].userId = watchDTO.userData!.id
                }
                
                CipherService.shared.saveCiphers(ciphers) {
                    if let previousUserId = previousUserId,
                       let currentUserid = watchDTO.userData?.id,
                       previousUserId != currentUserid {
                        CipherService.shared.deleteAll(previousUserId) {}
                    }
                    self.watchConnectivitySubject.send(WatchConnectivityMessage(state: nil))
                }
            }
        }
        catch {
            Log.e(error)
        }
    }
}
