# PayHub SMS Forwarder (Android)

বিকাশ, নগদ, রকেট, উপায় (যেগুলোর সিম এই ফোনে আছে) সবার "টাকা পেয়েছেন" SMS ধরে একই PayHub webhook URL-এ POST করে। কোনটা কোন সার্ভিসের, সেটা সার্ভার চেনে।
ডুয়াল সিমে একাধিক সিম একই অ্যাপে চলে। একাধিক ফোনেও একই URL বসানো যায়।
নেট না থাকলে/সার্ভার ডাউন থাকলে নিজে থেকে আবার চেষ্টা করে (WorkManager, রিবুটের পরও থাকে)।

## APK বানানোর দুই উপায়

### ১) GitHub Actions (Android Studio ছাড়া, সহজ)
1. এই ফোল্ডারটা একটা নতুন GitHub repo-তে আপলোড করুন।
2. Actions ট্যাব → "Build APK" → Run workflow।
3. শেষ হলে Artifacts থেকে `PayHubSmsForwarder-debug-apk` ডাউনলোড করুন, ভেতরে `app-debug.apk`।

### ২) Android Studio
Open → এই ফোল্ডার → Gradle sync → Build > Build APK(s)।
(gradle wrapper নেই; Android Studio নিজে সামলে নেয়।)

## ফোনে সেটআপ
1. APK ইনস্টল করুন (Unknown sources অনুমতি দিতে হবে)।
2. অ্যাপ খুলে PayHub ড্যাশবোর্ডের **SMS Webhook URL** পেস্ট করুন (অবশ্যই `https://`)।
3. Allowed senders ডিফল্ট `bKash,NAGAD,Rocket,16216,Upay` রাখুন (যেগুলো লাগে না মুছে দিন) → **Save**।
4. **1) Allow SMS permission** চাপুন।
   - Android 13+ এ সাইডলোড অ্যাপে পারমিশন আটকে গেলে: Settings → Apps → PayHub SMS Forwarder → ⋮ → **Allow restricted settings**, তারপর আবার চেষ্টা করুন।
5. **2) Disable battery optimization** চাপুন। কিছু ফোনে (Xiaomi, Oppo, Vivo, Samsung) আলাদাভাবে "Autostart"/"Background activity" চালু করতে হয়।
6. **Send connection test** চাপুন → লগে `OK 200 ... "ignored"` দেখলে URL ও secret ঠিক আছে।
7. একবার অ্যাপ খোলার পর এটা ব্যাকগ্রাউন্ডে চলে। অ্যাপ Force Stop করলে SMS ধরা বন্ধ হবে।

## লগের মানে
- `OK 200` → সার্ভার পেয়েছে
- `RETRY #n` → নেট/সার্ভার সমস্যা, আবার চেষ্টা হবে
- `FAILED 404` → URL বা secret ভুল (রোটেট করলে নতুন URL বসান)

## নিরাপত্তা
- শুধু https URL চলে।
- অ্যাপ শুধু RECEIVE_SMS ব্যবহার করে (SMS পড়ে না/পাঠায় না)। রিসিভার `BROADCAST_SMS` পারমিশনে সুরক্ষিত, তাই শুধু সিস্টেমই এতে SMS পাঠাতে পারে।
- Allowed senders ফাঁকা রাখলে সব SMS (OTP সহ) সার্ভারে যাবে, তাই তালিকা রাখুন।
