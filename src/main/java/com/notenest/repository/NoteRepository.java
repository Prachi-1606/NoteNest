package com.notenest.repository;

import com.notenest.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String title, String content);

    List<Note> findByFolder(String folder);

    List<Note> findByIsPinnedTrue();

    List<Note> findByTagsName(String tagName);

    /**
     * Returns rows of [year, month, day, count] for notes created on/after {@code since},
     * grouped by the date portion of createdAt. Uses standard JPA YEAR/MONTH/DAY
     * functions (portable across H2 dev and Postgres prod).
     */
    @Query("""
           SELECT YEAR(n.createdAt), MONTH(n.createdAt), DAY(n.createdAt), COUNT(n)
           FROM Note n
           WHERE n.createdAt >= :since
           GROUP BY YEAR(n.createdAt), MONTH(n.createdAt), DAY(n.createdAt)
           """)
    List<Object[]> findActivityCountsSince(@Param("since") LocalDateTime since);
}
