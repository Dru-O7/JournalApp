package net.engineeringdigest.journalApp.service;

import lombok.extern.slf4j.Slf4j;
import net.engineeringdigest.journalApp.dto.JournalEntryDTO;
import net.engineeringdigest.journalApp.entity.JournalEntry;
import net.engineeringdigest.journalApp.entity.User;
import net.engineeringdigest.journalApp.repository.JournalEntryRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JournalEntryService {
    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public JournalEntry saveEntry(JournalEntryDTO dto, String userName) {
        try {
            User user = userService.finduserByUserName(userName);

            JournalEntry journalEntry = convertToEntity(dto);
            JournalEntry saved = journalEntryRepository.save(journalEntry);

            user.getJournalEntries().add(saved);
            userService.saveUser(user);

            return saved;
        } catch (Exception e) {
            throw new RuntimeException("An error has occurred while saving the entry", e);
        }
    }
    @Transactional
    public JournalEntry saveEntry(JournalEntry journalEntry) {
        return journalEntryRepository.save(journalEntry);
    }


    private JournalEntry convertToEntity(JournalEntryDTO dto) {
        JournalEntry entry = new JournalEntry();
        entry.setTitle(dto.getTitle());
        entry.setContent(dto.getContent());
        entry.setDate(LocalDateTime.now());
        entry.setSentiment(dto.getSentiment());
        return entry;
    }

    public Optional<JournalEntry> findById(ObjectId id) {
        return journalEntryRepository.findById(id);
    }
    public List<JournalEntryDTO> getAll() {
        return journalEntryRepository.findAll()
                .stream()
                .map(entry -> new JournalEntryDTO(entry.getTitle(), entry.getContent(), entry.getSentiment()))
                .collect(Collectors.toList());
    }


    public boolean deleteById(ObjectId id, String userName) {
        boolean removed = false;
        try {
            User user = userService.finduserByUserName(userName);
            removed = user.getJournalEntries().removeIf(x -> x.getId().equals(id));
            if (removed) {
                userService.saveUser(user);
                journalEntryRepository.deleteById(id);
            }
        } catch (Exception e) {
            log.error("Error in Deleting entry");
            throw new RuntimeException("An error has occurred while deleting the entry", e);
        }
        return removed;
    }


}
