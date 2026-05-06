package com.notenest.repository;

import com.notenest.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String title, String content);

    List<Note> findByFolder(String folder);

    List<Note> findByIsPinnedTrue();

    List<Note> findByTagsName(String tagName);
}
