package com.notenest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteSearchResultDTO {

    private Long id;
    private String title;
    private String preview;
    private String folder;
    private LocalDateTime updatedAt;
    private List<String> matchedTags;
}
