# roadmap.md

## HeartStill — Geliştirme Yol Haritası

### Genel strateji
Projeyi bir anda “tam uygulama” olarak değil, kademeli olarak geliştireceğiz.  
İlk öncelik: **çalışan sensör toplama + tek ekranlı motion-aware UI**.  
İkinci öncelik: **DSP ile BPM tahmini**.  
Üçüncü öncelik: **güven skoru, debug kayıtları, AI için veri hazırlığı**.

---

## Faz 0 — Proje kurulumu

### Amaç
Temiz temel oluşturmak.

### Yapılacaklar
1. Android Studio projesi oluştur
2. Kotlin + Jetpack Compose başlat
3. Material 3 temelini kur
4. Karanlık tema + custom color tokens oluştur
5. Paket yapısını ayır
6. Temel `MainActivity` ve tek ekran `HomeScreen` oluştur

### Çıktı
- boş ama temiz tek ekranlı uygulama
- custom theme hazır

---

## Faz 1 — Sensör laboratuvarı

### Amaç
Önce gerçekten veri alıp alamadığımızı doğrulamak.

### Yapılacaklar
1. `SensorManager` katmanı yaz
2. Şu sensörleri dinle:
   - `TYPE_ACCELEROMETER`
   - `TYPE_LINEAR_ACCELERATION`
   - `TYPE_GYROSCOPE`
3. Her event için:
   - timestamp
   - x/y/z
   - sensor type
4. Sample rate hesapla
5. Verileri buffer içinde tut
6. Basit start / stop recording desteği ekle
7. CSV export ekle

### UI
Debug modda:
- canlı sensör değerleri
- sample rate
- recording status

### Kabul kriterleri
- sensör akışı stabil
- CSV dosyası düzgün oluşuyor
- farklı cihazlarda crash olmuyor

---

## Faz 2 — Hareket tespiti ve UI durumları

### Amaç
Nabızdan önce hareketi doğru yorumlamak.

### Motion score fikri
Aşağıdaki özelliklerden türet:
- son 1–2 sn accelerometer varyansı
- gyroscope enerji değeri
- orientation değişimi
- spike / jerk tespiti

### Motion state eşikleri
- yüksek hareket
- geçiş / sakinleşiyor
- sabit

### Yapılacaklar
1. `MotionAnalyzer` sınıfı oluştur
2. Her 100–250 ms’de motion score hesapla
3. State machine kur:
   - `HIGH_MOTION`
   - `SETTLING`
   - `STILL`
4. UI’yı bu state’e bağla

### UI davranışı
#### High motion
- kırmızı gradient
- yönlendirme metni:
  - “Yatar pozisyona geçin”
  - “Telefonu kalbinizin üzerine koyun”

#### Settling
- kırmızı azalır
- siyahlık artar
- metin:
  - “Sabit kalın”
  - “Ölçüm için hazırlanıyor”

#### Still
- neredeyse siyah ekran
- kalp simgesi görünür
- “Hazır”

### Kabul kriterleri
- hareketli durumda ekran doğru tepki veriyor
- sabit kalındığında doğal bir şekilde lock hissi oluşuyor

---

## Faz 3 — Modern tek ekranlı tasarımın rafinesi

### Amaç
Uygulamayı “çok iyi hissettirmek”.

### Yapılacaklar
1. Animated gradient arka plan
2. Breathing glow efekti
3. Kalp ikonuna pulse animasyonu
4. Metin geçişleri için yumuşak fade/slide
5. Motion state’e göre renk ve blur değişimi
6. Çok sade mikro tipografi ayarı
7. Premium loading / searching hissi

### Tasarım notları
- her şey siyah üzerinde sakin görünmeli
- gereksiz buton kalabalığı yok
- debug modu kapalıyken ekran aşırı temiz kalmalı

### Kabul kriterleri
- UI tek başına bile etkileyici görünmeli
- durumlar arası geçiş sert olmamalı
- prototip “tasarım ürünü” gibi hissettirmeli

---

## Faz 4 — Offline sinyal analizi altyapısı

### Amaç
Canlı BPM’ye geçmeden önce algoritmayı sağlamlaştırmak.

### Yapılacaklar
1. Kayıt alınmış CSV dosyalarını uygulama dışına aktar
2. Python notebook ile incele
3. Şunları test et:
   - raw accelerometer
   - linear acceleration
   - gyroscope
4. Şu filtreleri karşılaştır:
   - respiration band: `0.1–0.7 Hz`
   - cardiac band: `5–30 Hz`
   - alternatif bantlar: `3–20`, `1–25`, `6–25`
5. Her kanal için kalite analizi yap
6. Envelope yaklaşımını test et
7. Peak ve IBI kestirimini dene
8. Referans HR ile karşılaştır

### Test senaryoları
- nefes tutma
- normal nefes
- derin nefes
- göğüs ortası
- sol göğüs
- farklı vücut pozisyonları

### Kabul kriterleri
- en az birkaç kayıtta makul BPM yakınsaması
- hangi kanal/bant daha iyi anlaşılıyor

---

## Faz 5 — Cihaz üstünde gerçek zamanlı DSP

### Amaç
Artık uygulama canlı BPM göstermeye başlasın.

### Yapılacaklar
1. `SignalProcessor` modülü yaz
2. Ring buffer yapısı kur
3. Belirli aralıklarla pencere analiz et
4. Cardiac band filtering uygula
5. Envelope çıkar
6. Beat candidates bul
7. IBI hesapla
8. BPM üret
9. Confidence score üret

### Measurement state’leri
- `SEARCHING_PULSE`
- `PULSE_DETECTED`
- `LOW_CONFIDENCE`
- `NO_PULSE`

### UI
#### Searching
- kalp simgesi
- “Nabız aranıyor”

#### Detected
- büyük BPM sayısı
- kalite etiketi

#### No pulse
- “Nabız algılanmadı”
- yeniden konumlandırma önerisi

### Kabul kriterleri
- sabit kullanımda zaman zaman doğruya yakın BPM verebiliyor
- ölçüm yoksa yanıltıcı sayı basmıyor

---

## Faz 6 — Confidence ve kullanıcı güveni

### Amaç
Yanlış güven vermemek.

### Confidence için girdiler
- motion stability
- beat interval consistency
- channel agreement
- SNR tahmini
- respiration interference

### Yapılacaklar
1. confidence score formülü tanımla
2. score’u kategorilere ayır:
   - yüksek
   - orta
   - düşük
3. UI metinlerini buna göre değiştir
4. Düşük güvende kullanıcıyı yeniden yönlendir

### Kabul kriterleri
- uygulama belirsiz durumda dürüst davranıyor
- kullanıcı neden ölçüm alamadığını anlayabiliyor

---

## Faz 7 — Hidden debug / recording mode

### Amaç
Geliştirme ve veri toplama için araç eklemek.

### Yapılacaklar
1. gizli debug menüsü ekle
2. session recording metadata ekle
3. optional reference BPM alanı ekle
4. CSV/JSON export ekle
5. test session labeling ekle

### Etiket örnekleri
- `normal_breathing`
- `breath_hold`
- `deep_breathing`
- `center_chest`
- `left_chest`

### Kabul kriterleri
- veri toplama rahat
- seanslar düzenli kaydediliyor

---

## Faz 8 — AI/ML opsiyonel araştırma fazı

### Amaç
Klasik DSP yetmezse yükseltme yolu.

### Ne zaman?
- yeterli veri birikince
- cihazlar arası tutarsızlık görülürse
- artifact rejection problemi büyürse

### Olası adımlar
1. veri seti hazırlama
2. pencere etiketleme
3. baseline model:
   - 1D CNN
   - TCN
4. usable/unusable window classifier
5. heartbeat probability head
6. Kotlin veya ONNX/TFLite entegrasyonu

### Not
Bu faz opsiyoneldir. V1 için şart değildir.

---

## Önerilen sprint planı

### Sprint 1
- Faz 0 + Faz 1
- çıktı: sensör okuyucu ve CSV export

### Sprint 2
- Faz 2 + Faz 3
- çıktı: modern tek ekranlı motion-aware UI

### Sprint 3
- Faz 4
- çıktı: Python’da doğrulanmış filtre ve kanal seçimi

### Sprint 4
- Faz 5
- çıktı: canlı BPM prototipi

### Sprint 5
- Faz 6 + Faz 7
- çıktı: confidence + debug + recording mode

### Sprint 6+
- Faz 8
- çıktı: opsiyonel AI araştırması

---

## İlk teslim için önerilen minimal kapsam (MVP)

Eğer hızlı MVP istiyorsak ilk teslim şunlardan oluşmalı:

1. Tek ekranlı premium arayüz
2. Motion detection
3. Hareket durumuna göre ekran geçişleri
4. Sensör kayıt altyapısı
5. “Ready / Searching / No pulse” state’leri
6. Basit BPM prototipi veya placeholder logic

Bu MVP bile çok değerli olur çünkü hem tasarım hem teknik temel aynı anda kurulmuş olur.

---

## Son söz

Doğru ilerleme sırası:

**önce veri → sonra hareket → sonra UI → sonra BPM → sonra güven → en son AI**

En büyük hata, doğrudan “AI ile nabız ölçen uygulama” yapmaya çalışmak olur.  
En doğru yaklaşım, önce çok iyi tasarlanmış bir **tek ekranlı sensör ürünü** çıkarmaktır.