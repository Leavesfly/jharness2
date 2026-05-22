package io.leavesfly.jharness2.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @NotBlank(message = "Message must not be blank")
    @Size(max = 32000, message = "Message too long")
    private String message;

    private String model;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
