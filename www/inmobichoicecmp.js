const exec = require('cordova/exec');


function isFunction(functionObj) {
    return typeof functionObj === 'function';
}

function callNative(name, params = [], successCallback = null, errorCallback = null) {
    cordova.exec(successCallback, errorCallback, 'InMobiChoiceCMPPlugin', name, params);
}


let InMobiChoiceCMP = {
    initialized: false,

    isInitialized: function () {
        return this.initialized;
    },

    startChoice: (packageId, pCode, successCallback) => {
        console.log("InMobiChoiceCMP js -> showToast");
        callNative('startChoice', [packageId, pCode], function (config) {
            InMobiChoiceCMP.initialized = true;

            if (isFunction(successCallback)) {
                successCallback(config);
            }
        });
    },

    forceDisplayUI: function () {
        callNative('forceDisplayUI');
    },

    getConsentData: function (successCallback) {
        callNative('getConsentData', [],function (config) {
            if (isFunction(successCallback)) {
                successCallback(config);
            }
        });
    },

    getDataFromPreference: function (keys, successCallback) {
        callNative('getDataFromPreference', keys, function (config) {
            if (isFunction(successCallback)) {
                successCallback(config);
            }
        });
    }
};

// Attach listeners for sdk event methods

window.addEventListener('onCCPAConsentGiven', function (response) {
});
window.addEventListener('onCmpError', function (response) {
});
window.addEventListener('onCmpLoaded', function (response) {
});
window.addEventListener('onCMPUIStatusChanged', function (response) {
});
window.addEventListener('onGoogleBasicConsentChange', function (response) {
});
window.addEventListener('onGoogleVendorConsentGiven', function (response) {
});
window.addEventListener('onIABVendorConsentGiven', function (response) {
});
window.addEventListener('onNonIABVendorConsentGiven', function (response) {
});
window.addEventListener('onReceiveUSRegulationsConsent', function (response) {
});

if (typeof module !== undefined && module.exports) {
    module.exports = InMobiChoiceCMP;
}