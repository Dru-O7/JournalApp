package net.engineeringdigest.journalApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.engineeringdigest.journalApp.enums.Sentiment;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryDTO {
    private String title;
    private String content;
    private Sentiment sentiment;
}
