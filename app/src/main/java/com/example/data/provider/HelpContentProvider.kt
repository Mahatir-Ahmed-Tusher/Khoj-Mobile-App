package com.example.data.provider

import com.example.data.model.HelpArticle

object HelpContentProvider {
    fun getArticles(): List<HelpArticle> = listOf(
        HelpArticle(
            id = "fact_checker",
            titleEn = "Fact Checker",
            titleBn = "ফ্যাক্ট চেকার",
            contentEn = """
                ## What it does
                The Fact Checker allows you to verify rumors, news, or claims using AI.
                
                ## How it works
                Khoj analyzes the claim, searches authoritative sources across the web, and compares the evidence to generate a final verdict.
                
                ## How to use it
                1. Open the Home screen.
                2. Enter a claim, paste a URL, or upload an image.
                3. Tap "Search" to begin the investigation.
                
                ## Limitations
                - Extremely recent events might lack sufficient web evidence.
                - Opinion pieces cannot be fact-checked objectively.
                
                ## Tips
                - Be as specific as possible when typing a claim.
            """.trimIndent(),
            contentBn = """
                ## এটি কী করে
                ফ্যাক্ট চেকার এআই ব্যবহার করে গুজব, খবর বা যেকোনো দাবি যাচাই করতে সাহায্য করে।
                
                ## এটি কীভাবে কাজ করে
                খোঁজ আপনার দাবি বিশ্লেষণ করে, নির্ভরযোগ্য উৎস থেকে তথ্য খোঁজে এবং প্রমাণের ভিত্তিতে রায় প্রদান করে।
                
                ## কীভাবে ব্যবহার করবেন
                ১. হোম স্ক্রিনে যান।
                ২. কোনো দাবি লিখুন, লিঙ্ক পেস্ট করুন বা ছবি আপলোড করুন।
                ৩. তদন্ত শুরু করতে "সার্চ" চাপুন।
                
                ## সীমাবদ্ধতা
                - অতি সাম্প্রতিক ঘটনা সম্পর্কে ইন্টারনেটে পর্যাপ্ত প্রমাণ নাও থাকতে পারে।
                - ব্যক্তিগত মতামত ফ্যাক্টচেক করা সম্ভব নয়।
                
                ## টিপস
                - দাবি লেখার সময় যতটা সম্ভব সুনির্দিষ্ট হোন।
            """.trimIndent(),
            tags = listOf("fact", "check", "rumor", "news", "ফ্যাক্ট", "চেক", "গুজব", "খবর")
        ),
        HelpArticle(
            id = "deep_search",
            titleEn = "Deep Search",
            titleBn = "ডিপ সার্চ",
            contentEn = """
                ## What it does
                Deep Search conducts extensive research on complex topics and synthesizes a comprehensive markdown report.
                
                ## How it works
                It breaks down your query into multiple sub-queries, searches across dozens of sources, extracts relevant information, and compiles a highly detailed report.
                
                ## How to use it
                Enable "Deep Search" from the Home screen before entering your query.
                
                ## Limitations
                - Takes significantly longer than a regular fact-check.
            """.trimIndent(),
            contentBn = """
                ## এটি কী করে
                ডিপ সার্চ জটিল বিষয়গুলোর ওপর ব্যাপক গবেষণা করে এবং একটি বিস্তারিত রিপোর্ট তৈরি করে।
                
                ## এটি কীভাবে কাজ করে
                এটি আপনার অনুসন্ধানকে একাধিক অংশে ভাগ করে, অসংখ্য উৎস থেকে তথ্য সংগ্রহ করে এবং একটি পূর্ণাঙ্গ রিপোর্ট তৈরি করে।
                
                ## কীভাবে ব্যবহার করবেন
                আপনার অনুসন্ধান শুরু করার আগে হোম স্ক্রিন থেকে "ডিপ সার্চ" চালু করুন।
                
                ## সীমাবদ্ধতা
                - সাধারণ সার্চের চেয়ে এটি অনেক বেশি সময় নেয়।
            """.trimIndent(),
            tags = listOf("deep", "search", "research", "report", "ডিপ", "সার্চ", "গবেষণা")
        ),
        HelpArticle(
            id = "khoj_chat",
            titleEn = "Khoj Chat",
            titleBn = "খোঁজ চ্যাট",
            contentEn = """
                ## What it does
                Khoj Chat is a conversational AI assistant that can answer general questions and assist with tasks.
                
                ## How it works
                It uses advanced Large Language Models to converse with you. If you ask it to verify a claim, it can automatically switch to Fact-Check mode.
                
                ## How to use it
                Open the side drawer by swiping from the left edge of the Home screen and start typing.
            """.trimIndent(),
            contentBn = """
                ## এটি কী করে
                খোঁজ চ্যাট একটি কৃত্রিম বুদ্ধিমত্তা সম্পন্ন চ্যাট অ্যাসিস্ট্যান্ট যা আপনার সাধারণ প্রশ্নের উত্তর দিতে পারে।
                
                ## এটি কীভাবে কাজ করে
                এটি উন্নত লার্জ ল্যাঙ্গুয়েজ মডেল ব্যবহার করে। আপনি যদি কোনো দাবি যাচাই করতে বলেন, এটি স্বয়ংক্রিয়ভাবে ফ্যাক্ট-চেক মোডে চলে যেতে পারে।
                
                ## কীভাবে ব্যবহার করবেন
                হোম স্ক্রিনের বাম দিক থেকে সোয়াইপ করে ড্রয়ার খুলুন এবং চ্যাট শুরু করুন।
            """.trimIndent(),
            tags = listOf("chat", "ai", "assistant", "চ্যাট", "এআই")
        ),
        HelpArticle(
            id = "view_on_other_apps",
            titleEn = "View on Other Apps",
            titleBn = "অন্য অ্যাপে দেখুন",
            contentEn = """
                ## What it does
                Allows you to fact-check text directly from other apps like WhatsApp or Facebook without opening the Khoj app.
                
                ## How it works
                When you select text in another app, a floating Khoj icon appears. Tapping it will overlay a quick fact-check window.
                
                ## How to use it
                1. Go to Settings -> Controls.
                2. Enable "View on Other Apps".
                3. Grant the required "Display over other apps" permission.
                
                ## Note
                Some device manufacturers aggressively kill background services. You may need to disable battery optimization for Khoj.
            """.trimIndent(),
            contentBn = """
                ## এটি কী করে
                Khoj অ্যাপ না খুলেই WhatsApp বা Facebook-এর মতো অন্য অ্যাপ থেকে সরাসরি টেক্সট ফ্যাক্টচেক করার সুবিধা দেয়।
                
                ## এটি কীভাবে কাজ করে
                অন্য অ্যাপে টেক্সট সিলেক্ট করলে একটি ভাসমান খোঁজ আইকন দেখা যায়। সেখানে ট্যাপ করলেই ফ্যাক্টচেক শুরু হয়।
                
                ## কীভাবে ব্যবহার করবেন
                ১. সেটিংস -> কন্ট্রোলস এ যান।
                ২. "অন্য অ্যাপে দেখুন" চালু করুন।
                ৩. প্রয়োজনীয় পারমিশন দিন।
                
                ## সতর্কতা
                কিছু মোবাইল কোম্পানি ব্যাকগ্রাউন্ড সার্ভিস বন্ধ করে দেয়। আপনাকে হয়তো Khoj এর জন্য ব্যাটারি অপ্টিমাইজেশন বন্ধ করতে হতে পারে।
            """.trimIndent(),
            tags = listOf("overlay", "floating", "whatsapp", "ওভারলে", "ভাসমান")
        ),
        HelpArticle(
            id = "ai_image_detection",
            titleEn = "AI Image Detection",
            titleBn = "এআই ছবি শনাক্তকরণ",
            contentEn = """
                ## What it does
                Detects whether an image was generated or manipulated by AI.
                
                ## How it works
                It analyzes pixel patterns and generative artifacts using Khoj's AI detection gateway to determine authenticity.
                
                ## How to use it
                1. Select an image for scanning from the Home screen.
                2. Wait for the AI analysis to complete.
            """.trimIndent(),
            contentBn = """
                ## এটি কী করে
                একটি ছবি এআই দ্বারা তৈরি বা এডিট করা কি না তা শনাক্ত করে।
                
                ## এটি কীভাবে কাজ করে
                এটি উন্নত কৃত্রিম বুদ্ধিমত্তা মডেল ব্যবহার করে ছবির পিক্সেল প্যাটার্ন ও এআই আর্টিফ্যাক্ট বিশ্লেষণ করে।
                
                ## কীভাবে ব্যবহার করবেন
                ১. হোম স্ক্রিন থেকে স্ক্যান করার জন্য একটি ছবি নির্বাচন করুন।
                ২. বিশ্লেষণ শেষ হওয়া পর্যন্ত অপেক্ষা করুন।
            """.trimIndent(),
            tags = listOf("image", "ai", "fake", "deepfake", "ছবি", "এআই", "ফেক")
        )
    )
}
