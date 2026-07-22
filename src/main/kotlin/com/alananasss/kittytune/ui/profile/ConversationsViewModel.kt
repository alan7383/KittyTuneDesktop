    package com.alananasss.kittytune.ui.profile
    
        import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.InboxConversation
    import kotlinx.coroutines.launch
    
    class ConversationsViewModel : ViewModel() {
        private val api = RetrofitClient.create()
    
        val conversations = mutableStateListOf<InboxConversation>()
        var isLoading by mutableStateOf(true)
        var currentUserUrn by mutableStateOf("")
    
        fun loadConversations() {
            viewModelScope.launch {
                isLoading = true
                try {
                    val meResponse = api.getMeMobile()
                    val me = meResponse.user
                    currentUserUrn = me.urn ?: "soundcloud:users:${me.id}"
                    val response = api.getInbox()
                    println("currentUserUrn: $currentUserUrn")
                    response.collection.forEach { conv ->
                        println("Conv ${conv.id}: betweenUsers=" + conv.betweenUsers.map { "id=${it.id}, urn=${it.urn}" })
                    }
                    conversations.clear()
                    conversations.addAll(response.collection)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    }


