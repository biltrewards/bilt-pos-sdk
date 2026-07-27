/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.display.InputPayload;
import com.bilt.pos.nexo.model.DeviceEnum;
import com.bilt.pos.nexo.model.DisplayOutput;
import com.bilt.pos.nexo.model.InfoQualifyEnum;
import com.bilt.pos.nexo.model.Input;
import com.bilt.pos.nexo.model.InputCommandEnum;
import com.bilt.pos.nexo.model.InputData;
import com.bilt.pos.nexo.model.InputRequest;
import com.bilt.pos.nexo.model.InputResponse;
import com.bilt.pos.nexo.model.MenuEntry;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.OutputFormatEnum;
import com.bilt.pos.nexo.model.OutputText;
import com.bilt.pos.nexo.model.PINRequest;
import com.bilt.pos.nexo.model.PINRequestTypeEnum;
import com.bilt.pos.nexo.model.PINResponse;
import com.bilt.pos.nexo.model.Response;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.input.ConfirmationOptions;
import com.bilt.pos.session.input.InputOptions;
import com.bilt.pos.session.input.MenuOptions;
import com.bilt.pos.session.input.MenuSelection;
import com.bilt.pos.session.input.PinMode;
import com.bilt.pos.session.input.PinOptions;
import com.bilt.pos.session.input.PinResult;
import com.bilt.pos.session.input.Signature;

import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Builds Nexo {@code InputRequest}/{@code PINRequest} messages and parses
 * their responses into the typed values of the session input API.
 */
public final class InputManager {

    private final NexoExchange exchange;

    public InputManager(NexoExchange exchange) {
        this.exchange = exchange;
    }

    // ─── Native inputs ───

    public String digitString(String prompt, InputOptions options) {
        Input input = requestInput(InputCommandEnum.DIGIT_STRING,
                displayPayload(prompt, options), inputData(options), options.getTimeout());
        return input.getDigitInput();
    }

    public BigDecimal decimalString(String prompt, InputOptions options) {
        Input input = requestInput(InputCommandEnum.DECIMAL_STRING,
                displayPayload(prompt, options), inputData(options), options.getTimeout());
        String value = input.getDigitInput() != null ? input.getDigitInput() : input.getTextInput();
        if (value == null) {
            throw Wire.missing("decimal input");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                    "terminal returned a non-decimal value: " + value, null, e));
        }
    }

    public String textString(String prompt, InputOptions options) {
        Input input = requestInput(InputCommandEnum.TEXT_STRING,
                displayPayload(prompt, options), inputData(options), options.getTimeout());
        return input.getTextInput();
    }

    public boolean confirmation(String prompt, ConfirmationOptions options) {
        InputPayload payload = DisplayPayloadHelper.confirmation(prompt);
        applyButtons(payload, options);
        Input input = requestInput(InputCommandEnum.GET_CONFIRMATION,
                payload, InputData.builder(), options.getTimeout());
        return Boolean.TRUE.equals(input.getConfirmedFlag());
    }

    public MenuSelection menuEntry(String prompt, List<String> entries, MenuOptions options) {
        InputPayload payload = options.getAdditionalText() != null
                ? DisplayPayloadHelper.display(prompt, options.getAdditionalText())
                : DisplayPayloadHelper.display(prompt);
        MenuEntry[] menuEntries = new MenuEntry[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            menuEntries[i] = MenuEntry.builder()
                    .outputFormat(OutputFormatEnum.TEXT)
                    .outputText(new OutputText[] {
                            OutputText.builder().text(entries.get(i)).build()})
                    .build();
        }
        InputData.Builder menuData = InputData.builder();
        if (options.getTimeout() != null) {
            menuData.maxInputTime(options.getTimeout().getSeconds());
        }
        SaleToPOIRequest request = inputRequest(InputCommandEnum.GET_MENU_ENTRY, payload,
                menuData, menuEntries);
        Duration httpTimeout = options.getTimeout() == null
                ? null : options.getTimeout().plusSeconds(10);
        Input input = parseInput(exchange.sendExpectingBody(
                MessageCategoryType.INPUT, request, httpTimeout));
        long[] selected = input.getMenuEntryNumber();
        if (selected == null || selected.length == 0) {
            throw Wire.missing("menu selection");
        }
        List<Integer> indices = new ArrayList<>(selected.length);
        List<String> values = new ArrayList<>(selected.length);
        for (long number : selected) {
            int index = (int) number - 1;  // wire menu entries are 1-based
            if (index < 0 || index >= entries.size()) {
                throw new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                        "terminal selected menu entry " + number
                                + " outside the offered range"));
            }
            indices.add(index);
            values.add(entries.get(index));
        }
        return new MenuSelection(indices, values);
    }

    // ─── XSD-based inputs ───

    public Signature signature(String prompt) {
        SaleToPOIRequest request = inputRequest(InputCommandEnum.GET_CONFIRMATION,
                DisplayPayloadHelper.signature(prompt), InputData.builder(), null);
        SaleToPOIResponse response = exchange.sendExpectingBody(MessageCategoryType.INPUT, request);
        Input input = parseInput(response);
        if (!Boolean.TRUE.equals(input.getConfirmedFlag())) {
            throw new SessionException(new SessionError(SessionErrorCode.CANCELLED,
                    "the customer declined to sign"));
        }
        String image = response.getInputResponse().getInputResult()
                .getResponse().getAdditionalResponse();
        if (image == null) {
            throw Wire.missing("signature image");
        }
        return Signature.fromPng(Base64.getDecoder().decode(image), Instant.now());
    }

    public boolean amountConfirmation(BigDecimal amount, String prompt, String currency) {
        InputPayload payload = DisplayPayloadHelper.confirmation(prompt);
        payload.getDisplay().getText().add(currency + " " + amount.toPlainString());
        Input input = requestInput(InputCommandEnum.GET_CONFIRMATION,
                payload, InputData.builder(), null);
        return Boolean.TRUE.equals(input.getConfirmedFlag());
    }

    // ─── PIN ───

    public PinResult pin(PinMode mode, PinOptions options) {
        PINRequest.Builder pinRequest = PINRequest.builder()
                .pinRequestType(toWire(mode));
        if (options.getTimeout() != null) {
            pinRequest.maxWaitingTime(options.getTimeout().getSeconds());
        }
        if (options.getKeyReference() != null) {
            pinRequest.keyReference(options.getKeyReference());
        }
        if (options.getPinVerificationMethod() != null) {
            pinRequest.pinVerifMethod(options.getPinVerificationMethod());
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.DEVICE, MessageCategoryType.PIN))
                .pinRequest(pinRequest.build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(MessageCategoryType.PIN, request);
        PINResponse body = response.getPinResponse();
        if (body == null) {
            throw Wire.missing("PINResponse");
        }
        exchange.requireSuccess(MessageCategoryType.PIN, body.getResponse());
        boolean verified = mode != PinMode.PIN_ENTER
                && body.getResponse().getResult() != ResultType.FAILURE;
        return new PinResult(mode, verified, body.getCardholderPIN());
    }

    private static PINRequestTypeEnum toWire(PinMode mode) {
        switch (mode) {
            case PIN_ENTER:
                return PINRequestTypeEnum.PIN_ENTER;
            case PIN_VERIFY:
                return PINRequestTypeEnum.PIN_VERIFY;
            default:
                return PINRequestTypeEnum.PIN_VERIFY_ONLY;
        }
    }

    // ─── Plumbing ───

    private static InputPayload displayPayload(String prompt, InputOptions options) {
        List<String> lines = new ArrayList<>();
        if (options.getAdditionalText() != null) {
            lines.add(options.getAdditionalText());
        }
        if (options.getAdditionalText2() != null) {
            lines.add(options.getAdditionalText2());
        }
        return lines.isEmpty()
                ? DisplayPayloadHelper.display(prompt)
                : DisplayPayloadHelper.display(prompt, lines.toArray(new String[0]));
    }

    private static InputData.Builder inputData(InputOptions options) {
        InputData.Builder data = InputData.builder();
        if (options.getMaxLength() != null) {
            data.maxLength(options.getMaxLength().longValue());
        }
        if (options.getMinLength() != null) {
            data.minLength(options.getMinLength().longValue());
        }
        if (options.getTimeout() != null) {
            data.maxInputTime(options.getTimeout().getSeconds());
        }
        return data;
    }

    private static void applyButtons(InputPayload payload, ConfirmationOptions options) {
        if (options.getConfirmButton() != null) {
            payload.getConfirmation().setConfirmButton(options.getConfirmButton());
        }
        if (options.getCancelButton() != null) {
            payload.getConfirmation().setCancelButton(options.getCancelButton());
        }
    }

    private Input requestInput(InputCommandEnum command, InputPayload payload,
                               InputData.Builder inputData, Duration timeout) {
        SaleToPOIRequest request = inputRequest(command, payload, inputData, null);
        // give the HTTP read timeout headroom over the terminal-side input window
        Duration httpTimeout = timeout == null ? null : timeout.plusSeconds(10);
        return parseInput(exchange.sendExpectingBody(
                MessageCategoryType.INPUT, request, httpTimeout));
    }

    private SaleToPOIRequest inputRequest(InputCommandEnum command, InputPayload payload,
                                          InputData.Builder inputData, MenuEntry[] menuEntries) {
        String encoded;
        try {
            encoded = DisplayPayloadHelper.toBase64(payload);
        } catch (JAXBException e) {
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "failed to serialize input payload", null, e));
        }
        DisplayOutput.Builder displayOutput = DisplayOutput.builder()
                .device(DeviceEnum.CUSTOMER_DISPLAY)
                .infoQualify(InfoQualifyEnum.DISPLAY)
                .outputContent(OutputContent.builder()
                        .outputFormat(OutputFormatEnum.XHTML)
                        .outputXHTML(encoded)
                        .build());
        if (menuEntries != null) {
            displayOutput.menuEntry(menuEntries);
        }
        return SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.DEVICE, MessageCategoryType.INPUT))
                .inputRequest(InputRequest.builder()
                        .displayOutput(displayOutput.build())
                        .inputData(inputData
                                .device(DeviceEnum.CUSTOMER_INPUT)
                                .infoQualify(InfoQualifyEnum.INPUT)
                                .inputCommand(command)
                                .build())
                        .build())
                .build();
    }

    private Input parseInput(SaleToPOIResponse response) {
        InputResponse body = response.getInputResponse();
        if (body == null || body.getInputResult() == null) {
            throw Wire.missing("InputResponse");
        }
        Response result = body.getInputResult().getResponse();
        exchange.requireSuccess(MessageCategoryType.INPUT, result);
        Input input = body.getInputResult().getInput();
        if (input == null) {
            throw Wire.missing("input value");
        }
        return input;
    }
}
