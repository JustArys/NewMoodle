package com.example.newmoodle.mapper;

import com.example.newmoodle.dto.FeedbackDto;
import com.example.newmoodle.dto.SubmissionSimpleDto;
import com.example.newmoodle.dto.UserSimpleDto;
import com.example.newmoodle.model.Feedback;
import com.example.newmoodle.model.Submission;
import com.example.newmoodle.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring") // Creates a Spring Bean for this mapper
public interface FeedbackMapper {

    // Maps User Entity to UserSimpleDto (Uses fields from your User entity)
    @Mappings({
            @Mapping(target = "id", source = "user.id"),
            @Mapping(target = "fullName", source = "user.fullName"),
            @Mapping(target = "email", source = "user.email")
    })
    UserSimpleDto userToUserSimpleDto(User user);

    // Maps Submission Entity to SubmissionSimpleDto
    @Mappings({
            @Mapping(target = "id", source = "submission.id"),
            @Mapping(target = "fileUrl", source = "submission.fileUrl"),
            @Mapping(target = "status", source = "submission.status"),
            @Mapping(target = "student", source = "submission.student") // MapStruct uses userToUserSimpleDto
            // Optional: Add assignment mapping if included in DTO
            // @Mapping(target="assignmentId", source="submission.assignment.id"),
            // @Mapping(target="assignmentTitle", source="submission.assignment.title")
    })
    SubmissionSimpleDto submissionToSubmissionSimpleDto(Submission submission);


    // Maps Feedback Entity to FeedbackDto
    @Mappings({
            @Mapping(target = "id", source = "feedback.id"), // Assuming Feedback entity has an ID
            @Mapping(target = "aiGenerated", source = "feedback.aiGenerated"),
            @Mapping(target = "submission", source = "feedback.submission"), // MapStruct uses submissionToSubmissionSimpleDto
            @Mapping(target = "teacher", source = "feedback.teacher")       // MapStruct uses userToUserSimpleDto
            // Optional: Map other Feedback fields if needed
            // @Mapping(target = "createdAt", source = "feedback.createdAt")
    })
    FeedbackDto feedbackToFeedbackDto(Feedback feedback);
}