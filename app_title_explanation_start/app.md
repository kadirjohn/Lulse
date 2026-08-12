# app.md

## HeartStill — Android Nabız Algılama Uygulaması PRD / Product Spec

### 1. Proje Özeti

**HeartStill**, kullanıcının telefonu göğsünün/kalbinin üzerine koyarak, telefonun hareket sensörlerinden (accelerometer, linear acceleration, gyroscope) yararlanıp **dinlenme anındaki nabzı temassız sayılabilecek şekilde** tahmin etmeye çalışan, **tek ekranlı**, çok modern, çok sade, karanlık temalı bir Android uygulamasıdır.

Uygulamanın ana kullanım senaryosu:

- Kullanıcı yatakta veya kanepe gibi sabit bir yüzeyde yatıyor
- Telefonunu göğsünün/kalbinin üzerine koyuyor
- Uygulama ortam hareketini ve telefon sabitliğini değerlendiriyor
- Hareket yeterince azalınca ölçüm moduna geçiyor
- Nabız tespit ederse tahmini BPM gösteriyor
- Nabız tespit edemezse açıklayıcı bir mesaj veriyor

Bu uygulama **medikal cihaz değildir**. İlk hedef, doğruya yakın bir **wellness / experimentation / personal tracking** deneyimi sunmaktır.

---

## 2. Hedefler

### Ana hedefler
- Android üzerinde telefon sensörleri ile göğüs üstünden nabız algılama
- Çok sade ama çok premium hissettiren **tek ekranlı UI**
- Hareketliyken kullanıcıyı doğru yönlendirme
- Ölçüm güvenilir değilse bunu dürüstçe söyleme
- İlk versiyonda backend olmadan tamamen cihaz üstünde çalışabilme

### İkincil hedefler
- Ham sensör verisini kaydedebilme
- CSV export veya debug recording modu
- Gerekirse sonraki aşamada model eğitimi için veri toplama altyapısı
- İleride küçük ML / AI tabanlı artifact rejection ekleyebilme

### Hedef olmayanlar (V1)
- Klinik doğruluk iddiası
- Egzersiz sırasında veya yoğun hareket halinde doğru ölçüm
- Çoklu ekranlı karmaşık onboarding
- Sosyal özellikler, hesap sistemi, cloud sync

---

## 3. Platform ve Teknoloji Kararı

### Platform
- **Android native**
- Minimum öneri: Android 10+
- Geliştirme dili: **Kotlin**

### UI teknolojisi
- **Jetpack Compose**
- Sebep:
  - modern animasyonlar
  - tek ekranlı UI için çok uygun
  - hızlı iterasyon
  - custom transitions ve glow / gradient animasyonları kolay

### Mimari
- **MVVM + Clean-ish module separation**
- Coroutines + Flow / StateFlow
- Sensör pipeline ile UI kesin ayrılmalı

### Neden Flutter değil?
Bu proje için ilk aşamada native Android daha mantıklı:
- yüksek frekansta sensör erişimi
- timestamp ve sampling kontrolü
- düşük seviye sinyal işleme
- performans ve stabilite

---

## 4. Ürün Fikri ve Ana Deneyim

Uygulama **tek bir arayüzden** oluşur. Ekran durumlara göre dönüşür. Kullanıcı hiçbir ayar yapmadan doğrudan yönlendirilir.

### Temel durumlar (state machine)

1. **Idle / Başlangıç**
2. **Too Much Motion / Fazla Hareket**
3. **Settling / Hareket Azalıyor**
4. **Ready / Ölçüme Hazır**
5. **Measuring / Nabız Aranıyor**
6. **Pulse Detected / Nabız Bulundu**
7. **No Pulse Detected / Nabız Algılanmadı**
8. **Low Confidence / Güven Düşük**

---

## 5. Tasarım Dili

### Görsel yön
Arayüz hissi:
- çok koyu siyah arka plan
- minimal
- premium
- “Apple Find My AirTag” benzeri **temiz, odaklı, modern, yumuşak hareketli**
- fakat birebir kopya değil; sadece estetik ilham

### Tasarım prensipleri
- tek odak noktası
- çok az metin
- sinyal / hareket durumunu renk ve animasyonla anlatma
- hata mesajını bile estetik biçimde verme
- yoğun veri ekranı değil, “ambient interface”

### Renk sistemi
- Arka plan: gerçek siyaha yakın (`#000000` taban)
- Hareketli durumda: koyu kırmızı / bordo / sıcak gradient
- Sakinleşme durumunda: kırmızıdan koyu gri/siyaha yumuşak geçiş
- Başarılı lock durumunda: siyah + yumuşak beyaz / koyu gri glow
- Nabız bulunduğunda: beyaz / hafif sıcak beyaz kalp vurgusu
- Güven düşerse: hafif amber / soluk kırmızı uyarı

### Tipografi
- modern sans
- Android tarafında:
  - Inter
  - SF benzeri bir hissiyat için temiz bir sans
- büyük, okunaklı, sakin tipografi
- gereksiz ikon kalabalığı yok

---

## 6. Ana UI Akışı

### A. Fazla hareket var
Arka plan:
- koyu siyah üstünde kırmızıya çalan hareketli gradient
- gradient hafif “breathing” ve akış hissi verir

Ortadaki ana mesaj:
- **“Yatar pozisyona geçin”**
- alt satır: **“Telefonu kalbinizin üzerine koyun”**

Ek küçük bilgi:
- “Hareket azalınca ölçüm başlayacak”

Davranış:
- motion score yüksekse bu ekran aktif kalır
- arka plan canlı ve biraz huzursuz hissettirir

---

### B. Hareket azalıyor
Arka plan:
- kırmızı etkisi azalır
- siyahlık artar
- gradient yavaşlar
- UI kullanıcıya “yaklaşıyorsun” hissi verir

Metin:
- **“Sabit kalın”**
- alt satır: “Ölçüm için hazırlanıyor”

İsteğe bağlı mikro feedback:
- bir halka veya blur glow küçülerek stabilize olabilir

---

### C. Ölçüme hazır / lock acquired
Arka plan:
- neredeyse tam siyah
- çok hafif nefes alan glow

Ortada:
- kalp simgesi
- kalp çok hafif pulse animation yapar

Metin:
- **“Hazır”**
- alt satır: “Nabız aranıyor”

---

### D. Nabız algılandı
Ortada büyük şekilde:
- **72 BPM** gibi sayı
- üstte veya ortada kalp ikonu
- kalp BPM ile senkron mikro pulse yapabilir

Alt bilgi:
- “Tahmini nabız”
- “Sinyal kalitesi: Yüksek / Orta / Düşük”

Ek bilgi:
- gerekirse küçük confidence göstergesi
- örn. `Güven: %91`

---

### E. Nabız algılanmadı
Bu durum özellikle önemli.

Koşul:
- telefon yeterince sabit olmasına rağmen belirli süre içinde anlamlı heartbeat pattern çıkmıyorsa

Ekran:
- siyah arka plan
- hafif nötr glow
- kalp ikonu daha sönük

Metin:
- **“Nabız algılanmadı”**
- alt satır:
  - “Telefonu biraz sola kaydırmayı deneyin”
  - veya “Telefonu kalbinizin üzerine daha sabit yerleştirin”

Bu ekranda kullanıcıya tekrar yön verilmeli, ama suçlayıcı olmadan.

---

### F. Düşük güven
Örnek:
- BPM bulundu ama sinyal çok dalgalı
- motion artifact yüksek
- sensör uyumu zayıf

Metin:
- **“Ölçüm kararsız”**
- alt satır: “Biraz daha sabit kalın”

Bu durumda BPM gösterilebilir ama küçük etiketle:
- `72 BPM`
- `Düşük güven`

---

## 7. Tek Ekranlı Bilgi Mimarisi

Ekranda her zaman en fazla şu alanlar olsun:

1. Arka plan animasyonu
2. Ortada ana mesaj / kalp / BPM
3. Alt bölümde çok kısa açıklama
4. Debug kapalıyken başka hiçbir kalabalık öğe yok

### Debug modu açılırsa
Gizli/opsiyonel debug overlay:
- motion score
- sampling rate
- selected sensor channel
- estimated respiration rate
- confidence
- raw signal indicator

Bu normal kullanıcıya gösterilmez.

---

## 8. Sensörler

V1’de kaydedilecek sensörler:
- `TYPE_ACCELEROMETER`
- `TYPE_LINEAR_ACCELERATION`
- `TYPE_GYROSCOPE`

Her olay için:
- timestamp
- x
- y
- z

### Önemli not
İlk aşamada uygulama yalnızca tek bir sensöre güvenmemeli. Çünkü cihazdan cihaza kalite değişebilir.

---

## 9. Sinyal İşleme Yaklaşımı

### Felsefe
İlk sürüm için **AI zorunlu değil**. Önce klasik dijital sinyal işleme.

### Pipeline

#### A. Motion gating
Önce telefon yeterince sabit mi?
- son birkaç saniyedeki toplam hareket enerjisi
- orientation değişimi
- jiroskop varyansı
- büyük gövde hareketi tespiti

Sonuç:
- `motionScore` üret

Eğer score yüksekse:
- ölçüm yapılmaz
- kullanıcı yönlendirilir

#### B. Respiration channel
Düşük frekanslı banttan solunumu çıkar:
- yaklaşık `0.1–0.7 Hz`

Amaç:
- solunumun varlığını anlamak
- hareket paternini yorumlamak
- gerekirse solunum sırasında confidence azaltmak

#### C. Cardiac channel
Kalp titreşimi için ayrı kanal:
- yaklaşık `5–30 Hz` ile başlanabilir
- farklı parametreler test edilerek optimize edilir

Bu kanalda:
- accelerometer ve gyroscope ayrı ayrı incelenir
- kanal kalite puanı hesaplanır
- en iyi kanal veya en iyi kombinasyon seçilir

#### D. Envelope + beat candidate detection
Doğrudan ham peak saymak yerine:
- rectification / squaring
- moving RMS veya envelope
- smoothing
- adaptive threshold

Sonra:
- beat adayları çıkar

#### E. Beat validation
- minimum beat interval kontrolü
- beklenen fizyolojik aralık kontrolü
- kanal uyumu
- periodiklik kontrolü

#### F. BPM estimation
- IBI (inter-beat interval) hesapla
- son birkaç IBI üzerinde median / robust average
- `BPM = 60 / IBI`

#### G. Confidence score
Aşağıdaki faktörlerden türetilir:
- motion stability
- accelerometer / gyroscope agreement
- beat interval consistency
- signal-to-noise estimate
- envelope clarity

---

## 10. Önerilen State Machine

### `MotionState`
- `HIGH_MOTION`
- `SETTLING`
- `STILL`

### `MeasurementState`
- `IDLE`
- `WAITING_FOR_STILLNESS`
- `SEARCHING_PULSE`
- `PULSE_DETECTED`
- `NO_PULSE`
- `LOW_CONFIDENCE`

UI doğrudan bu state’lere bağlanmalı.

---

## 11. İlk Çalıştırma / Onboarding

Ayrı onboarding ekranı istemiyoruz. Tek ekran içinde çok hafif yönlendirme olsun.

İlk açılışta:
- kısa bir bilgi katmanı:
  - “Yatın ve telefonu kalbinizin üzerine koyun”
  - “En iyi sonuç için birkaç saniye sabit kalın”
- “Devam” butonu gerekmez; doğrudan sensör izleme başlar

İsteğe bağlı:
- ilk kullanımda mini alt yazı:
  - “Bu uygulama tıbbi amaçla kullanılmaz.”

---

## 12. Veri Toplama Modu (Opsiyonel ama Tavsiye)

İleride AI/ML gerekirse bunun için başlangıçtan bir veri altyapısı bırakılmalı.

### Recording session metadata
- device model
- app version
- sample rate
- body position
- phone placement (center chest / left chest)
- breathing condition (normal / hold / deep)
- optional reference BPM
- start/end timestamps

### Session types
- normal breathing
- breath hold
- deep breathing
- left chest placement
- center chest placement

### Export
- CSV / JSON
- her seans ayrı klasör

Bu mod debug/hidden olabilir.

---

## 13. AI / ML Gelecek Planı

V1’de AI yok.  
V2+ için opsiyonel fikirler:

### Kullanım alanları
- artifact rejection
- heartbeat candidate classification
- confidence estimation
- sensor fusion learning

### Olası model
- küçük 1D CNN / TCN
- giriş: 2–5 saniyelik çok kanallı IMU pencere verisi
- çıkış:
  - heartbeat probability
  - usable / unusable window
  - confidence

### Uyarı
ML ancak yeterli gerçek veri toplanınca eklenmeli.

---

## 14. Uygulama İçindeki Ana Metinler

### High motion
- “Yatar pozisyona geçin”
- “Telefonu kalbinizin üzerine koyun”
- “Hareket azalınca ölçüm başlayacak”

### Settling
- “Sabit kalın”
- “Ölçüm için hazırlanıyor”

### Ready / Searching
- “Hazır”
- “Nabız aranıyor”

### Pulse detected
- “Tahmini nabız”
- “Sinyal kalitesi yüksek”
- “Güven: %91”

### No pulse
- “Nabız algılanmadı”
- “Telefonu biraz daha sola kaydırmayı deneyin”
- “Birkaç saniye daha sabit kalın”

### Low confidence
- “Ölçüm kararsız”
- “Biraz daha sabit kalın”

---

## 15. Başarı Kriterleri

V1 başarılı sayılır eğer:
- uygulama açıldığında sensörlerden düzenli veri alıyorsa
- motion gating düzgün çalışıyorsa
- UI motion’a göre akıcı geçiş yapıyorsa
- kullanıcı durunca “ölçüme hazır” durumuna geçiyorsa
- bazı kullanımlarda makul BPM verebiliyorsa
- sinyal zayıfsa dürüstçe “algılanmadı” diyorsa

---

## 16. Teknik Riskler

- bazı telefonlarda sensör kalitesi zayıf olabilir
- telefonun yerleşimi sonuçları ciddi etkileyebilir
- kullanıcının nefesi veya mikro hareketleri ölçümü bozabilir
- linear acceleration bazı cihazlarda üreticiye göre değişebilir
- gyroscope her cihazda aynı kaliteyi vermeyebilir

Bu yüzden:
- debug verisi
- recording mode
- confidence scoring
zorunlu kabul edilmelidir.

---

## 17. Non-Functional Requirements

- uygulama tek elde/tek bakışta anlaşılır olmalı
- 60 FPS’e yakın akıcı UI hissi olmalı
- sensör callback’leri UI thread’i tıkamamalı
- battery drain kabul edilebilir seviyede olmalı
- measurement başlatma gecikmesi düşük olmalı
- animasyonlar kaliteli ama abartısız olmalı

---

## 18. Önerilen Paket Yapısı

```text
com.heartstill.app
├── data
│   ├── sensor
│   ├── recording
│   └── model
├── domain
│   ├── motion
│   ├── signal
│   ├── measurement
│   └── confidence
├── ui
│   ├── screen
│   ├── components
│   ├── animation
│   └── theme
└── debug
```

---

## 19. Sonuç

Bu ürünün gücü:
- “telefonu göğse koy, sabit kal, hissedilen kalp titreşimini estetik bir deneyime dönüştür” fikrinde
- tek ekranlı sakin ama premium bir deneyimde
- dürüst ölçüm yaklaşımında
- gelecekte AI ile daha da iyileşebilecek bir sensör altyapısında

**HeartStill** ilk aşamada bir sağlık laboratuvarı değil; çok iyi tasarlanmış, deneysel, modern, hissiyatı güçlü bir **single-screen heart sensing app** olacaktır.