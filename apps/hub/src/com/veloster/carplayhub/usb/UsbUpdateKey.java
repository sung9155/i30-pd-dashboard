package com.veloster.carplayhub.usb;

/**
 * Public half of the i30 PD build's USB update key (RSA 2048, X.509 SubjectPublicKeyInfo DER, SHA-256
 * 1bb3cb032be721b98726190ab29c28e430e0f817ae77712345a965692e2c3b29; made on 2026-11-28, not upstream's key). The private half signs
 * manifest.txt (scripts/usb_update.py); it is not in this repository. Distinct from the APK signing key on purpose
 * (UsbBundle).
 */
public final class UsbUpdateKey {
    private UsbUpdateKey() {}
    public static final String DER = "30820122300d06092a864886f70d01010105000382010f003082010a02820101009e215ca63bdc0219f8cb3193fa7fafe29c87e5990c6143e61e3efd6a3d2fa1d9c539110452b11afb333d5520b9aa73f86e3f497edd0bea043845358d60fa7c683a8429c94f1e6ec76690453ece56a7a04b35a47630237974e0e7ef25c36d6c0673c986aeaa7b43ff31d1bb1fc4fc700c32a7b4aed0236eeccb244e30a86f104a953d78109728ecf859c814a4f0c0ffb74497c7d86dcf14e54da085d4ded797bf7a8c2ad9ee3cb3b67a3336acfcf159c4549bf6d720abd00e8308d69de678514817c4b628c92f7491b42b45db58bcc1fdeb8cad904af8d2ef7d40168d8e02c3198e1389560b68e566ac26a62b8c43e183aacf9843483bd7f93537f697786fd2f10203010001";
}
