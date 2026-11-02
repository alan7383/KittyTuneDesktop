"""Desktop-only Kitty pet strings (the Android ones are imported by import_android_pet.py).

  python -I scripts/pet/pet_desktop_strings.py

Where Android talks about the home screen, the widget, tapping or pinching, the desktop has a
floating window, clicks and the mouse wheel. Written between their own markers in the 7 string
files, so re-running replaces the block.
"""
import os
import re

DESKTOP = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
I18N = os.path.join(DESKTOP, 'src', 'main', 'resources', 'i18n')
BEGIN = '    <!-- KITTY PET DESKTOP (scripts/pet/pet_desktop_strings.py) -->'
END = '    <!-- /KITTY PET DESKTOP -->'

S = {
    'pet_desk_settings_sub': {
        'en': 'Your little desktop Tamagotchi',
        'fr': 'Ton petit Tamagotchi de bureau',
        'de': 'Dein kleines Desktop-Tamagotchi',
        'hu': 'A kis asztali Tamagotchid',
        'ru': 'Твой маленький тамагочи на рабочем столе',
        'tr': 'Masaüstündeki küçük Tamagotchi\\\'n',
        'vi': 'Tamagotchi nhỏ trên màn hình máy tính của bạn',
    },
    'pet_desk_hello_body': {
        'en': 'A tiny pixel cat that lives on your desktop and loves your music. Let\\\'s set it up together.',
        'fr': 'Un tout petit chat en pixels qui vit sur ton bureau et adore ta musique. On le prépare ensemble ?',
        'de': 'Eine winzige Pixelkatze, die auf deinem Desktop wohnt und deine Musik liebt. Richten wir sie gemeinsam ein.',
        'hu': 'Egy apró pixelcica, amely az asztalodon lakik és imádja a zenédet. Állítsuk be együtt!',
        'ru': 'Крошечный пиксельный котик, который живёт на твоём рабочем столе и обожает твою музыку. Давай настроим его вместе.',
        'tr': 'Masaüstünde yaşayan ve müziğini seven minicik bir piksel kedi. Hadi birlikte hazırlayalım.',
        'vi': 'Một chú mèo pixel bé xíu sống trên màn hình máy tính và rất mê nhạc của bạn. Cùng thiết lập nhé.',
    },
    'pet_desk_window_title': {
        'en': 'Put it on your desktop',
        'fr': 'Installe-le sur ton bureau',
        'de': 'Setz sie auf deinen Desktop',
        'hu': 'Tedd ki az asztalodra',
        'ru': 'Помести его на рабочий стол',
        'tr': 'Masaüstüne koy',
        'vi': 'Đặt nó lên màn hình',
    },
    'pet_desk_window_body': {
        'en': 'Your cat lives in a little floating window, always on top, so it can keep you company while you work.',
        'fr': 'Ton chat vit dans une petite fenêtre flottante, toujours au premier plan, pour te tenir compagnie pendant que tu travailles.',
        'de': 'Deine Katze wohnt in einem kleinen schwebenden Fenster, immer im Vordergrund, und leistet dir beim Arbeiten Gesellschaft.',
        'hu': 'A cicád egy kis lebegő ablakban lakik, mindig legfelül, hogy társaságod legyen munka közben.',
        'ru': 'Твой котик живёт в маленьком плавающем окне поверх остальных, чтобы составлять тебе компанию за работой.',
        'tr': 'Kedin her zaman üstte duran küçük, yüzen bir pencerede yaşar; çalışırken sana eşlik eder.',
        'vi': 'Chú mèo sống trong một cửa sổ nổi nhỏ, luôn ở trên cùng, để bầu bạn khi bạn làm việc.',
    },
    'pet_desk_show': {
        'en': 'Show it on the desktop',
        'fr': 'L\\\'afficher sur le bureau',
        'de': 'Auf dem Desktop zeigen',
        'hu': 'Megjelenítés az asztalon',
        'ru': 'Показать на рабочем столе',
        'tr': 'Masaüstünde göster',
        'vi': 'Hiện trên màn hình',
    },
    'pet_desk_show_sub': {
        'en': 'A little floating window, always on top. Drag it anywhere, right-click for options',
        'fr': 'Une petite fenêtre flottante, toujours au premier plan. Déplace-la où tu veux, clic droit pour les options',
        'de': 'Ein kleines schwebendes Fenster, immer im Vordergrund. Zieh es irgendwohin, Rechtsklick für Optionen',
        'hu': 'Egy kis lebegő ablak, mindig legfelül. Húzd bárhová, jobb klikk a beállításokhoz',
        'ru': 'Маленькое плавающее окно поверх остальных. Перетащи куда угодно, правый клик — настройки',
        'tr': 'Her zaman üstte duran küçük, yüzen bir pencere. İstediğin yere sürükle, seçenekler için sağ tıkla',
        'vi': 'Một cửa sổ nổi nhỏ, luôn ở trên cùng. Kéo đi bất cứ đâu, nhấp chuột phải để xem tùy chọn',
    },
    'pet_desk_hide': {
        'en': 'Hide the cat',
        'fr': 'Cacher le chat',
        'de': 'Katze ausblenden',
        'hu': 'Cica elrejtése',
        'ru': 'Спрятать котика',
        'tr': 'Kediyi gizle',
        'vi': 'Ẩn chú mèo',
    },
    'pet_desk_tray_show': {
        'en': 'Show the kitty pet',
        'fr': 'Afficher le chat',
        'de': 'Kätzchen anzeigen',
        'hu': 'Cica megjelenítése',
        'ru': 'Показать котика',
        'tr': 'Kediyi göster',
        'vi': 'Hiện chú mèo',
    },
    'pet_desk_window_section': {
        'en': 'Pet window',
        'fr': 'Fenêtre du chat',
        'de': 'Katzenfenster',
        'hu': 'Cica ablak',
        'ru': 'Окно котика',
        'tr': 'Kedi penceresi',
        'vi': 'Cửa sổ thú cưng',
    },
    'pet_desk_how_boop': {
        'en': 'Click it to boop it. Click the bubble to feed it some music',
        'fr': 'Clique dessus pour lui faire un boop. Clique sur la bulle pour lui donner de la musique',
        'de': 'Klick sie an für einen Boop. Klick auf die Blase, um sie mit Musik zu füttern',
        'hu': 'Kattints rá egy pöckölésért. Kattints a buborékra, hogy zenét adj neki',
        'ru': 'Кликни по нему, чтобы бупнуть. Кликни по облачку, чтобы накормить его музыкой',
        'tr': 'Boop\\\'lamak için tıkla. Biraz müzikle beslemek için baloncuğa tıkla',
        'vi': 'Nhấp vào để boop nó. Nhấp vào bong bóng để cho nó nghe nhạc',
    },
    'pet_desk_tap_hint': {
        'en': 'Click it to boop it ♡',
        'fr': 'Clique dessus pour un boop ♡',
        'de': 'Klick sie an für einen Boop ♡',
        'hu': 'Kattints rá egy pöckölésért ♡',
        'ru': 'Кликни, чтобы бупнуть ♡',
        'tr': 'Boop\\\'lamak için tıkla ♡',
        'vi': 'Nhấp vào để boop nó ♡',
    },
    'pet_desk_tap': {
        'en': 'When you click the cat',
        'fr': 'Quand tu cliques sur le chat',
        'de': 'Wenn du die Katze anklickst',
        'hu': 'Ha a cicára kattintasz',
        'ru': 'Когда кликаешь по котику',
        'tr': 'Kediye tıkladığında',
        'vi': 'Khi bạn nhấp vào chú mèo',
    },
    'pet_desk_announce_body': {
        'en': 'Meet your pixel pet: it lives on your desktop, dances to your music, gets bored when it is quiet and has plenty of surprises. You can even make it look like your own cat from a photo.',
        'fr': 'Voici ton animal en pixels : il vit sur ton bureau, danse sur ta musique, s\\\'ennuie quand c\\\'est calme et réserve plein de surprises. Tu peux même lui donner l\\\'apparence de ton propre chat à partir d\\\'une photo.',
        'de': 'Lerne dein Pixel-Haustier kennen: Es wohnt auf deinem Desktop, tanzt zu deiner Musik, langweilt sich, wenn es still ist, und steckt voller Überraschungen. Du kannst es sogar per Foto wie deine eigene Katze aussehen lassen.',
        'hu': 'Ismerd meg a pixel kedvencedet: az asztalodon lakik, táncol a zenédre, unatkozik, ha csend van, és rengeteg meglepetést tartogat. Akár egy fotó alapján a saját cicádra is hasonlíthat.',
        'ru': 'Знакомься с пиксельным питомцем: он живёт на рабочем столе, танцует под твою музыку, скучает в тишине и полон сюрпризов. Его даже можно сделать похожим на твоего кота по фотографии.',
        'tr': 'Piksel dostunla tanış: masaüstünde yaşar, müziğinle dans eder, ortalık sessizken sıkılır ve bolca sürprizi vardır. Bir fotoğrafla onu kendi kedine bile benzetebilirsin.',
        'vi': 'Làm quen với thú cưng pixel: nó sống trên màn hình máy tính, nhảy theo nhạc của bạn, buồn chán khi yên tĩnh và có vô số bất ngờ. Bạn còn có thể biến nó giống chú mèo của mình từ một bức ảnh.',
    },
    'pet_desk_exclusives_sub': {
        'en': 'Digs through your likes, judges them, cheers for your top artist (stays on your computer)',
        'fr': 'Fouille dans tes j\\\'aime, les juge, encourage ton artiste préféré (reste sur ton ordinateur)',
        'de': 'Wühlt in deinen Likes, bewertet sie, feuert deinen Top-Künstler an (bleibt auf deinem Computer)',
        'hu': 'Átnézi a kedvelt dalaidat, véleményezi őket, szurkol a kedvenc előadódnak (a gépeden marad)',
        'ru': 'Роется в твоих лайках, оценивает их, болеет за любимого артиста (всё остаётся на компьютере)',
        'tr': 'Beğenilerini karıştırır, onları yargılar, en sevdiğin sanatçıyı alkışlar (bilgisayarında kalır)',
        'vi': 'Lục lọi bài đã thích, chấm điểm chúng, cổ vũ nghệ sĩ hàng đầu của bạn (chỉ lưu trên máy tính)',
    },
    'pet_desk_photo_body': {
        'en': 'Pick a photo of your cat and an algorithm turns it into its pixel twin: coat colours, pattern and eyes.',
        'fr': 'Choisis une photo de ton chat et un algorithme le transforme en jumeau pixelisé : couleurs du pelage, motif et yeux.',
        'de': 'Wähle ein Foto deiner Katze und ein Algorithmus macht daraus ihren Pixel-Zwilling: Fellfarben, Muster und Augen.',
        'hu': 'Válassz egy fotót a cicádról, és egy algoritmus pixel ikertestvért készít belőle: bundaszínek, minta és szemek.',
        'ru': 'Выбери фото своего кота, и алгоритм превратит его в пиксельного двойника: цвета шерсти, узор и глаза.',
        'tr': 'Kedinin bir fotoğrafını seç, bir algoritma onu piksel ikizine dönüştürsün: tüy renkleri, desen ve gözler.',
        'vi': 'Chọn ảnh chú mèo của bạn và thuật toán sẽ biến nó thành bản sao pixel: màu lông, hoa văn và đôi mắt.',
    },
    'pet_desk_photo_card_body': {
        'en': 'Pick a photo of your real cat and get its pixel twin',
        'fr': 'Choisis une photo de ton vrai chat et obtiens son jumeau en pixels',
        'de': 'Wähle ein Foto deiner echten Katze und erhalte ihren Pixel-Zwilling',
        'hu': 'Válassz egy fotót az igazi cicádról, és kapd meg pixel ikerét',
        'ru': 'Выбери фото настоящего кота и получи его пиксельного двойника',
        'tr': 'Gerçek kedinin bir fotoğrafını seç ve piksel ikizini al',
        'vi': 'Chọn ảnh chú mèo thật của bạn và nhận bản sao pixel',
    },
    'pet_desk_pick_photo': {
        'en': 'Choose a photo',
        'fr': 'Choisir une photo',
        'de': 'Foto auswählen',
        'hu': 'Fotó kiválasztása',
        'ru': 'Выбрать фото',
        'tr': 'Fotoğraf seç',
        'vi': 'Chọn ảnh',
    },
    'pet_desk_crop_hint': {
        'en': 'Drag to move and scroll to zoom so its face fills the circle',
        'fr': 'Fais glisser pour déplacer et utilise la molette pour zoomer, afin que sa tête remplisse le cercle',
        'de': 'Ziehen zum Verschieben, scrollen zum Zoomen, bis das Gesicht den Kreis füllt',
        'hu': 'Húzással mozgasd, görgetéssel nagyíts, hogy a feje kitöltse a kört',
        'ru': 'Перетаскивай и крути колёсико, чтобы мордочка заполнила круг',
        'tr': 'Yüzü daireyi dolduracak şekilde sürükleyerek taşı, kaydırarak yakınlaştır',
        'vi': 'Kéo để di chuyển và cuộn để phóng to sao cho mặt mèo lấp đầy vòng tròn',
    },
    'pet_desk_editor_hint': {
        'en': 'Click or drag on the cat to paint, pixel by pixel',
        'fr': 'Clique ou fais glisser sur le chat pour peindre, pixel par pixel',
        'de': 'Klick oder zieh über die Katze, um Pixel für Pixel zu malen',
        'hu': 'Kattints vagy húzd az egeret a cicán a festéshez, pixelről pixelre',
        'ru': 'Кликай или веди мышью по котику, чтобы рисовать пиксель за пикселем',
        'tr': 'Piksel piksel boyamak için kediye tıkla veya sürükle',
        'vi': 'Nhấp hoặc kéo trên chú mèo để tô, từng pixel một',
    },
    'pet_desk_rare_tip': {
        'en': 'Psst… very rare moments can show up in the pet window. Each one you see is added here!',
        'fr': 'Psst… des moments très rares peuvent apparaître dans la fenêtre du chat. Chacun que tu vois est ajouté ici !',
        'de': 'Psst… im Katzenfenster können sehr seltene Momente auftauchen. Jeder, den du siehst, landet hier!',
        'hu': 'Pszt… nagyon ritka pillanatok is felbukkanhatnak a cica ablakában. Mindegyik, amit látsz, ide kerül!',
        'ru': 'Тсс… в окне котика могут случаться очень редкие моменты. Каждый увиденный попадает сюда!',
        'tr': 'Psst… kedi penceresinde çok nadir anlar belirebilir. Gördüğün her biri buraya eklenir!',
        'vi': 'Suỵt… những khoảnh khắc cực hiếm có thể xuất hiện trong cửa sổ thú cưng. Mỗi lần bạn thấy sẽ được thêm vào đây!',
    },
    'pet_desk_bar_button_desc': {
        'en': 'Shows or hides the cat\'s floating window',
        'fr': 'Affiche ou cache la fenêtre flottante du chat',
        'de': 'Zeigt oder verbirgt das schwebende Fenster der Katze',
        'hu': 'Megjeleníti vagy elrejti a cica lebegő ablakát',
        'ru': 'Показывает или прячет плавающее окно котика',
        'tr': 'Kedinin yüzen penceresini gösterir veya gizler',
        'vi': 'Hiện hoặc ẩn cửa sổ nổi của chú mèo',
    },
    'pet_desk_photo_filter': {
        'en': 'Images',
        'fr': 'Images',
        'de': 'Bilder',
        'hu': 'Képek',
        'ru': 'Изображения',
        'tr': 'Görseller',
        'vi': 'Hình ảnh',
    },
}

LANGS = ['en', 'fr', 'de', 'hu', 'ru', 'tr', 'vi']


def esc(v):
    return v.replace('&', '&amp;').replace('<', '&lt;')


for lang in LANGS:
    path = os.path.join(I18N, f'strings-{lang}.xml')
    text = open(path, encoding='utf-8').read()
    lines = [f'    <string name="{k}">{esc(v[lang])}</string>' for k, v in S.items()]
    block = BEGIN + '\n' + '\n'.join(lines) + '\n' + END + '\n'
    if BEGIN in text:
        text = re.sub(re.escape(BEGIN) + r'.*?' + re.escape(END) + r'\n', lambda _: block, text, flags=re.S)
    else:
        idx = text.rindex('</resources>')
        text = text[:idx] + block + text[idx:]
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(text)
    print(lang, len(lines))
