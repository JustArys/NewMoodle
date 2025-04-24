package com.example.newmoodle.service;

import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.Subject;
import com.example.newmoodle.repository.SectionRepository;
import com.example.newmoodle.repository.SubjectRepository;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubjectServer {
    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final SectionService sectionService;

    public Subject saveSubject(String name) {
        var subject = Subject.builder()
                .name(name);
        return subjectRepository.save(subject.build());
    }
    public List<Subject> getSubjects(){
        return subjectRepository.findAll();
    }



    public Subject getSubjectById(Long id) {
        return subjectRepository.findById(id).orElseThrow(()
                -> new NoSuchElementException(String.format("Subject with id '%d' not found", id)));
    }

    public Subject updateSubject(Long id,Subject subject) {
        Subject subject1 = subjectRepository.getReferenceById(id);
        subject1.setName(subject.getName());
        return subjectRepository.save(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        // 1. Найти Subject или выбросить исключение
        Subject subject = getSubjectById(id);

        // 2. Получить ID всех связанных секций
        // Важно скопировать ID в новый список, так как удаление секции может модифицировать
        // оригинальную коллекцию subject.getSections() во время итерации, если она используется напрямую.
        List<Long> sectionIdsToDelete = subject.getSections() // Получаем связанные секции
                .stream()
                .map(Section::getId) // Извлекаем их ID
                .collect(Collectors.toList()); // Собираем в новый список

        // 3. Удалить каждую связанную секцию через SectionService
        // Это гарантирует, что логика удаления Assignments и их файлов будет выполнена
        for (Long sectionId : sectionIdsToDelete) {
            try {
                sectionService.deleteSection(sectionId);
            } catch (Exception e) {
                // Логируем ошибку удаления секции, но продолжаем (или решаем прервать)
                System.err.println("Error deleting section with id '" + sectionId +
                        "' while deleting subject id '" + id + "': " + e.getMessage());
                // Можно пробросить исключение, если одна ошибка должна остановить весь процесс
                // throw new RuntimeException("Failed to delete associated section " + sectionId + ", subject deletion aborted.", e);
            }
        }

        // 4. После удаления всех секций, удалить сам Subject
        // CascadeType.ALL и orphanRemoval=true на Subject.sections позаботятся об удалении
        // связи в базе данных, но мы уже удалили секции явно через сервис.
        // Теперь просто удаляем Subject.
        subjectRepository.delete(subject);
    }
}
