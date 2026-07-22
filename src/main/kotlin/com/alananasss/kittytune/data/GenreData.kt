    package com.alananasss.kittytune.data
    
    import com.alananasss.kittytune.core.str
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.ui.graphics.vector.ImageVector
    import com.alananasss.kittytune.R
    
    // shared data model for categories
    data class SearchCategory(
        val id: String,
        val title: String,
        val query: String,
        val icon: ImageVector
    )
    
    object GenreData {
    
        // helper to get string resources
            
        fun getMoods(): List<SearchCategory> {
            return listOf(
                SearchCategory("feelgood", str(R.string.category_mood_feelgood), "Feel Good", Icons.Rounded.Mood),
                SearchCategory("calm", str(R.string.category_mood_calm), "Calm Relax", Icons.Rounded.Spa),
                SearchCategory("focus", str(R.string.category_mood_focus), "Focus Study", Icons.Rounded.Psychology),
                SearchCategory("energy", str(R.string.category_mood_energy), "Energy", Icons.Rounded.LocalFireDepartment),
                SearchCategory("workout", str(R.string.category_mood_workout), "Workout", Icons.Rounded.FitnessCenter),
                SearchCategory("gaming", str(R.string.category_mood_gaming), "Gaming", Icons.Rounded.SportsEsports),
                SearchCategory("winter", str(R.string.category_mood_winter), "Winter", Icons.Rounded.AcUnit),
                SearchCategory("driving", str(R.string.category_mood_driving), "Driving", Icons.Rounded.DirectionsCar),
                SearchCategory("romance", str(R.string.category_mood_romance), "Romantic", Icons.Rounded.Favorite),
                SearchCategory("party", str(R.string.category_mood_party), "Party", Icons.Rounded.Celebration),
                SearchCategory("sleep", str(R.string.category_mood_sleep), "Sleep", Icons.Rounded.Bedtime),
                SearchCategory("sad", str(R.string.category_mood_sad), "Sad", Icons.Rounded.SentimentVeryDissatisfied)
            )
        }
    
        fun getGenres(): List<SearchCategory> {
            return listOf(
                SearchCategory("hiphop", str(R.string.category_genre_hiphop), "Hip Hop", Icons.Rounded.Mic),
                SearchCategory("rapfr", str(R.string.category_genre_rapfr), "Rap FR", Icons.Rounded.Mic),
                SearchCategory("phonk", str(R.string.category_genre_phonk_fix), "Phonk", Icons.Rounded.TimeToLeave),
                SearchCategory("rnb", str(R.string.category_genre_rnb), "R&B", Icons.Rounded.FavoriteBorder),
                SearchCategory("funk", str(R.string.category_genre_funk), "Funk", Icons.Rounded.Nightlife),
                SearchCategory("disco", str(R.string.category_genre_disco), "Disco", Icons.Rounded.Album),
    
                SearchCategory("dance", str(R.string.category_genre_dance), "EDM", Icons.Rounded.FlashOn),
                SearchCategory("house", str(R.string.category_genre_house), "House", Icons.Rounded.Nightlife),
                SearchCategory("hardstyle", str(R.string.category_genre_hardstyle), "Hardstyle", Icons.Rounded.Bolt),
                SearchCategory("trance", str(R.string.category_genre_trance), "Trance", Icons.Rounded.Waves),
                SearchCategory("dubstep", str(R.string.category_genre_dubstep), "Dubstep", Icons.Rounded.GraphicEq),
                SearchCategory("pluggnb", str(R.string.category_genre_pluggnb), "Pluggnb", Icons.Rounded.Cloud),
                SearchCategory("dreamcore", str(R.string.category_genre_dreamcore), "Dreamcore", Icons.Rounded.AutoAwesome),
                SearchCategory("glitchcore", str(R.string.category_genre_glitchcore), "Glitchcore", Icons.Rounded.SdCardAlert),
                SearchCategory("eurobeat", str(R.string.category_genre_eurobeat), "Eurobeat", Icons.Rounded.Bolt),
                SearchCategory("cloudrap", str(R.string.category_genre_cloudrap), "Cloud Rap", Icons.Rounded.WbCloudy),
                SearchCategory("drumandbass", str(R.string.category_genre_drumandbass), "Drum and Bass", Icons.Rounded.FastForward),
                SearchCategory("jungle", str(R.string.category_genre_jungle), "Jungle", Icons.Rounded.Forest),
                SearchCategory("breakcore", str(R.string.category_genre_breakcore), "Breakcore", Icons.Rounded.BrokenImage),
                SearchCategory("garage", str(R.string.category_genre_garage), "UK Garage", Icons.Rounded.Garage),
                SearchCategory("idm", str(R.string.category_genre_idm), "IDM", Icons.Rounded.Memory),
    
                SearchCategory("pop", str(R.string.category_genre_pop), "Pop", Icons.Rounded.Star),
                SearchCategory("hyperpop", str(R.string.category_genre_hyperpop), "Hyperpop", Icons.Rounded.Flare),
                SearchCategory("scenecore", str(R.string.category_genre_scenecore), "Scenecore", Icons.Rounded.Style),
                SearchCategory("digicore", str(R.string.category_genre_digicore), "Digicore", Icons.Rounded.DataObject),
                SearchCategory("vocaloid", str(R.string.category_genre_vocaloid), "Vocaloid", Icons.Rounded.Face),
                SearchCategory("lolicore", str(R.string.category_genre_lolicore), "Lolicore", Icons.Rounded.ChildCare),
                SearchCategory("anime", str(R.string.category_genre_anime), "Anime", Icons.Rounded.Animation),
                SearchCategory("weeb", str(R.string.category_genre_weeb), "Otaku", Icons.Rounded.AutoAwesome),
                SearchCategory("nightcore", str(R.string.category_genre_nightcore), "Nightcore", Icons.Rounded.HistoryToggleOff),
                SearchCategory("bedroompop", str(R.string.category_genre_bedroompop), "Bedroom Pop", Icons.Rounded.Bed),
                SearchCategory("popjp", str(R.string.category_genre_popjp), "J-Pop", Icons.Rounded.MusicNote),
                SearchCategory("kpop", str(R.string.category_genre_kpop), "K-Pop", Icons.Rounded.StarBorder),
                SearchCategory("popfr", str(R.string.category_genre_popfr), "Variété Française", Icons.Rounded.MusicNote),
                SearchCategory("urbanfr", str(R.string.category_genre_urbanfr), "Pop Urbaine", Icons.Rounded.Mic),
    
                SearchCategory("rock", str(R.string.category_genre_rock), "Rock", Icons.Rounded.Whatshot),
                SearchCategory("alt", str(R.string.category_genre_alt), "Alternative", Icons.Rounded.Album),
                SearchCategory("metal", str(R.string.category_genre_metal), "Metal", Icons.Rounded.Bolt),
                SearchCategory("emo", str(R.string.category_genre_emo), "Emo", Icons.Rounded.SentimentDissatisfied),
                SearchCategory("grunge", str(R.string.category_genre_grunge), "Grunge", Icons.Rounded.MusicOff),
                SearchCategory("shoegaze", str(R.string.category_genre_shoegaze), "Shoegaze", Icons.Rounded.Waves),
    
                SearchCategory("lofi", str(R.string.category_genre_lofi), "Lofi", Icons.Rounded.LocalCafe),
                SearchCategory("ambient", str(R.string.category_genre_ambient), "Ambient", Icons.Rounded.WbCloudy),
                SearchCategory("vaporwave", str(R.string.category_genre_vaporwave), "Vaporwave", Icons.Rounded.Computer),
                SearchCategory("synthwave", str(R.string.category_genre_synthwave), "Synthwave", Icons.Rounded.Brightness4),
    
                SearchCategory("latin", str(R.string.category_genre_latin), "Latin", Icons.Rounded.Public),
                SearchCategory("afro", str(R.string.category_genre_afro), "Afrobeat", Icons.Rounded.Public),
                SearchCategory("reggae", str(R.string.category_genre_reggae), "Reggae", Icons.Rounded.Public),
                SearchCategory("arabic", str(R.string.category_genre_arabic), "Arabic", Icons.Rounded.MusicNote),
                SearchCategory("bollywood", str(R.string.category_genre_bollywood), "Bollywood", Icons.Rounded.MusicNote),
                SearchCategory("brazil", str(R.string.category_genre_brazil), "Brazil", Icons.Rounded.Public),
                SearchCategory("afrocuban", str(R.string.category_genre_afrocuban), "Afro-Cuban", Icons.Rounded.Public),
                SearchCategory("celtic", str(R.string.category_genre_celtic), "Celtic", Icons.Rounded.Forest),
                SearchCategory("flamenco", str(R.string.category_genre_flamenco), "Flamenco", Icons.Rounded.LocalFireDepartment),
    
                SearchCategory("jazz", str(R.string.category_genre_jazz), "Jazz", Icons.Rounded.Piano),
                SearchCategory("blues", str(R.string.category_genre_blues), "Blues", Icons.Rounded.MusicNote),
                SearchCategory("classical", str(R.string.category_genre_classical), "Classical", Icons.Rounded.AccountBalance),
                SearchCategory("country", str(R.string.category_genre_country), "Country", Icons.Rounded.MusicNote),
                SearchCategory("folk", str(R.string.category_genre_folk), "Folk", Icons.Rounded.Forest),
                SearchCategory("gospel", str(R.string.category_genre_gospel), "Gospel", Icons.Rounded.Church),
                SearchCategory("soundtrack", str(R.string.category_genre_soundtrack), "Soundtrack", Icons.Rounded.Theaters),
                SearchCategory("decades", str(R.string.category_genre_decades), "80s 90s", Icons.Rounded.History),
                SearchCategory("family", str(R.string.category_genre_family), "Kids", Icons.Rounded.ChildCare)
            ).sortedBy { it.title }
        }
    }


