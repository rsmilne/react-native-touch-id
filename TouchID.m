#import "TouchID.h"
#import <React/RCTUtils.h>
#import "React/RCTConvert.h"

@implementation TouchID

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(isSupported: (RCTResponseSenderBlock)callback)
{
    LAContext *context = [[LAContext alloc] init];
    NSError *error;

    if ([context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:&error]) {
        callback(@[[NSNull null], [self getBiometryType:context]]);
        
    } else if ([context canEvaluatePolicy:LAPolicyDeviceOwnerAuthentication error:&error]) {
        callback(@[[NSNull null], [self getBiometryType:context]]);
    } else {
        callback(@[RCTMakeError(@"RCTTouchIDNotSupported", nil, nil)]);
        return;
    }
}

RCT_EXPORT_METHOD(authenticate: (NSString *)reason
                  options:(NSDictionary *)options
                  callback: (RCTResponseSenderBlock)callback)
{
    Boolean passcodeFallback = false;
    LAContext *context = [[LAContext alloc] init];
    NSError *error;

    if (RCTNilIfNull([options objectForKey:@"fallbackLabel"]) != nil) {
        NSString *fallbackLabel = [RCTConvert NSString:options[@"fallbackLabel"]];   
        context.localizedFallbackTitle = fallbackLabel;
    }

    if (RCTNilIfNull([options objectForKey:@"passcodeFallback"]) != nil) {
        passcodeFallback = [[RCTConvert NSNumber:options[@"passcodeFallback"]] boolValue];
    }

    // Only TouchID
    if (!passcodeFallback && [context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:&error]) {
        [context evaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics
                localizedReason:reason
                          reply:^(BOOL success, NSError *error)
         {
             [self handleAttemptToUseDeviceIDWithSuccess:success error:error callback:callback];
         }];

    // TouchID or passcode
    } else if (passcodeFallback && [context canEvaluatePolicy:LAPolicyDeviceOwnerAuthentication error:&error]) {
        [context evaluatePolicy:LAPolicyDeviceOwnerAuthentication
                localizedReason:reason
                          reply:^(BOOL success, NSError *error)
         {
             [self handleAttemptToUseDeviceIDWithSuccess:success error:error callback:callback];
         }];
    } else {
        [self handleAttemptToUseDeviceIDWithSuccess:nil error:error callback:callback];
        return;
    }
}

- (void)handleAttemptToUseDeviceIDWithSuccess:(BOOL)success error:(NSError *)error callback:(RCTResponseSenderBlock)callback {
    if (success) { // Authentication Successful
        callback(@[[NSNull null], @"Authenticated with Touch ID."]);
    } else if (error) { // Authentication Error
        NSString *errorReason;
        
        switch (error.code) {
            case LAErrorAuthenticationFailed:
                errorReason = @"LAErrorAuthenticationFailed";
                break;
                
            case LAErrorUserCancel:
                errorReason = @"LAErrorUserCancel";
                break;
                
            case LAErrorUserFallback:
                errorReason = @"LAErrorUserFallback";
                break;
                
            case LAErrorSystemCancel:
                errorReason = @"LAErrorSystemCancel";
                break;
                
            case LAErrorPasscodeNotSet:
                errorReason = @"LAErrorPasscodeNotSet";
                break;
                
            case LAErrorTouchIDNotAvailable:
                errorReason = @"LAErrorTouchIDNotAvailable";
                break;
                
            case LAErrorTouchIDNotEnrolled:
                errorReason = @"LAErrorTouchIDNotEnrolled";
                break;
                
            default:
                errorReason = @"RCTTouchIDUnknownError";
                break;
        }
        
        NSLog(@"Authentication failed: %@", errorReason);
        callback(@[RCTMakeError(errorReason, nil, nil)]);
    } else { // Authentication Failure
        callback(@[RCTMakeError(@"LAErrorAuthenticationFailed", nil, nil)]);
    }
}

- (NSString *)getBiometryType:(LAContext *)context
{
    if (@available(iOS 11, *)) {
        if (context.biometryType == LABiometryTypeFaceID) {
            return @"FaceID";
        }
        else if (context.biometryType == LABiometryTypeTouchID) {
            return @"TouchID";
        }
        else if (context.biometryType == LABiometryNone) {
            return @"None";
        }
    }

    return @"TouchID";
}

@end
