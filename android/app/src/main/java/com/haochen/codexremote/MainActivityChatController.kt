package com.haochen.codexremote

internal fun MainActivity.scrollConversationTo(target: ConversationScrollTarget) {
        if (conversationItems.isEmpty()) return
        chatScrollCommand =
            ConversationScrollCommand(
                target = target,
                nonce = System.nanoTime(),
            )
    }
