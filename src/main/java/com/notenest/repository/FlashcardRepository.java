package com.notenest.repository;

import com.notenest.model.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findBySourceNoteId(Long noteId);

    void deleteBySourceNoteId(Long noteId);
}
