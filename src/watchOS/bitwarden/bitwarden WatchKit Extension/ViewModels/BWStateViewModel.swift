import Foundation

class BWStateViewModel : ObservableObject{
    @Published var text:String
    @Published var isLoading:Bool = false
    
    init(_ state: BWState){
        switch state {
        case .needLogin:
            text = "LogInToBitwardenOnYourIPhoneToViewVerificationCodes"
//        case .needUnlock:
//            text = "UnlockBitwardenOnYourIPhoneToViewVerificationCodes"
        case .needPremium:
            text = "ToViewVerificationCodesUpgradeToPremium"
        case .needSetup:
            text = "SetUpBitwardenToViewItemsContainingVerificationCodes"
        case .syncing:
            text = "SyncingItemsContainingVerificationCodes"
            isLoading = true
        case .need2FAItem:
            text = "Add2FactorAutenticationToAnItemToViewVerificationCodes"
        default:
            text = ""
        }
    }
}
