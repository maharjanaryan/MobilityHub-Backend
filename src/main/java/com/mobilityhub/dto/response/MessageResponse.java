// dto/response/MessageResponse.java
package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private String message;
    private boolean success;
    private Object data;

    public MessageResponse(String message, boolean success) {
        this.message = message;
        this.success = success;
    }
}