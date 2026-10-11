package com.example.data.prompt

object SharedCitationPrompt {
    
    val INLINE_CITATION_RULES = """
        IMPORTANT CITATION RULES:
        1. Ground verified facts, numbers, dates, and official quotes in the provided EVIDENCE with inline citations [1], [2, 3].
        2. Format citations as [1], [2, 3] based on the 'Citation ID' provided in the Sources block.
        3. Do NOT invent or hallucinate Citation IDs. Only use IDs that are explicitly provided in the Sources block.
        4. Place citations immediately after the relevant sentence or verified statement.
        5. Media Attribution: When stating that reputable news media or official bodies confirmed or reported a fact from evidence, always attach the corresponding citation bracket (e.g., 'রয়টার্সের প্রতিবেদন অনুযায়ী [1]'). Do NOT assert that reputable media confirmed a fact without linking to evidence. (Note: discussing the absence of reports, e.g., 'মূলধারার কোনো গণমাধ্যমে এমন কোনো প্রতিবেদন পাওয়া যায়নি', or fake photocards, e.g., 'প্রথম আলোর লোগো নকল করে', in natural journalistic prose is fully expected and does not require citations).
        6. Section Exception: The 'যাচাই প্রক্রিয়া' section describes Khoj's internal methodology and does not require citations.
        7. At the end of your report, do NOT append a list of references, the UI will handle that.
    """.trimIndent()
    
}
