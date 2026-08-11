/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.nexo.model.DiagnosisRequest;
import com.bilt.pos.nexo.model.DiagnosisResponse;
import com.bilt.pos.nexo.model.GetTotalsRequest;
import com.bilt.pos.nexo.model.GetTotalsResponse;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.OutputFormatEnum;
import com.bilt.pos.nexo.model.OutputText;
import com.bilt.pos.nexo.model.PrintOutput;
import com.bilt.pos.nexo.model.PrintRequest;
import com.bilt.pos.nexo.model.PrintResponse;
import com.bilt.pos.nexo.model.ReconciliationRequest;
import com.bilt.pos.nexo.model.ReconciliationResponse;
import com.bilt.pos.nexo.model.ReconciliationTypeEnum;
import com.bilt.pos.nexo.model.ResponseModeEnum;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.SoundActionEnum;
import com.bilt.pos.nexo.model.SoundContent;
import com.bilt.pos.nexo.model.SoundFormatEnum;
import com.bilt.pos.nexo.model.SoundRequest;
import com.bilt.pos.nexo.model.TotalFilter;
import com.bilt.pos.session.internal.NexoExchange;
import com.bilt.pos.session.internal.NexoMessageFactory;
import com.bilt.pos.session.internal.Wire;

import java.util.Arrays;

/**
 * The wire bodies of the terminal's device and admin operations —
 * SERVICE/DEVICE-class nexo messages that carry no session reference. Owned
 * by {@link Terminal}; argument validation and lifecycle guards stay on the
 * public methods.
 */
final class TerminalServices {

    private final NexoExchange exchange;
    private final NexoMessageFactory factory;
    private final String storeLocation;

    TerminalServices(NexoExchange exchange, NexoMessageFactory factory, String storeLocation) {
        this.exchange = exchange;
        this.factory = factory;
        this.storeLocation = storeLocation;
    }

    DiagnosisResult diagnose() {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE,
                        MessageCategoryType.DIAGNOSIS))
                .diagnosisRequest(DiagnosisRequest.builder().build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.DIAGNOSIS, request);
        DiagnosisResponse body = response.getDiagnosisResponse();
        if (body == null) {
            throw Wire.missing("DiagnosisResponse");
        }
        exchange.requireSuccess(MessageCategoryType.DIAGNOSIS, body.getResponse());
        return new DiagnosisResult(body.getPoiStatus(),
                body.getHostStatus() == null ? null : Arrays.asList(body.getHostStatus()));
    }

    ReconciliationResult getTotals() {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE,
                        MessageCategoryType.GET_TOTALS))
                .getTotalsRequest(GetTotalsRequest.builder()
                        .totalFilter(TotalFilter.builder()
                                .saleID(factory.getSaleId())
                                .totalsGroupID(storeLocation)
                                .build())
                        .build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.GET_TOTALS, request);
        GetTotalsResponse body = response.getGetTotalsResponse();
        if (body == null) {
            throw Wire.missing("GetTotalsResponse");
        }
        exchange.requireSuccess(MessageCategoryType.GET_TOTALS, body.getResponse());
        return new ReconciliationResult(body.getPoiReconciliationID(),
                body.getTransactionTotals() == null
                        ? null : Arrays.asList(body.getTransactionTotals()));
    }

    ReconciliationResult reconcile() {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE,
                        MessageCategoryType.RECONCILIATION))
                .reconciliationRequest(ReconciliationRequest.builder()
                        .reconciliationType(ReconciliationTypeEnum.SALE_RECONCILIATION)
                        .build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.RECONCILIATION, request);
        ReconciliationResponse body = response.getReconciliationResponse();
        if (body == null) {
            throw Wire.missing("ReconciliationResponse");
        }
        exchange.requireSuccess(MessageCategoryType.RECONCILIATION, body.getResponse());
        return new ReconciliationResult(body.getPoiReconciliationID(),
                body.getTransactionTotals() == null
                        ? null : Arrays.asList(body.getTransactionTotals()));
    }

    Void print(PrintPayload payload) {
        OutputContent.Builder content = OutputContent.builder();
        if (payload.getFormat() == PrintPayload.Format.TEXT) {
            content.outputFormat(OutputFormatEnum.TEXT)
                    .outputText(new OutputText[] {OutputText.builder()
                            .text(payload.getContent())
                            .build()});
        } else {
            content.outputFormat(OutputFormatEnum.XHTML)
                    .outputXHTML(payload.getContent());
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.DEVICE,
                        MessageCategoryType.PRINT))
                .printRequest(PrintRequest.builder()
                        .printOutput(PrintOutput.builder()
                                .documentQualifier(payload.getDocumentQualifier())
                                .responseMode(ResponseModeEnum.PRINT_END)
                                .outputContent(content.build())
                                .build())
                        .build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.PRINT, request);
        PrintResponse body = response.getPrintResponse();
        if (body == null) {
            throw Wire.missing("PrintResponse");
        }
        exchange.requireSuccess(MessageCategoryType.PRINT, body.getResponse());
        return null;
    }

    Void playSound(String soundReferenceId, Integer volumePercent) {
        return sendSound(SoundActionEnum.START_SOUND,
                SoundContent.builder()
                        .soundFormat(SoundFormatEnum.SOUND_REF)
                        .referenceID(soundReferenceId)
                        .build(),
                volumePercent);
    }

    Void stopSound() {
        return sendSound(SoundActionEnum.STOP_SOUND, null, null);
    }

    private Void sendSound(SoundActionEnum action, SoundContent content, Integer volumePercent) {
        SoundRequest.Builder soundRequest = SoundRequest.builder()
                .soundAction(action)
                .responseMode(ResponseModeEnum.IMMEDIATE);
        if (content != null) {
            soundRequest.soundContent(content);
        }
        if (volumePercent != null) {
            soundRequest.soundVolume(volumePercent.longValue());
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.DEVICE, MessageCategoryType.SOUND))
                .soundRequest(soundRequest.build())
                .build();
        SaleToPOIResponse response = exchange.send(MessageCategoryType.SOUND, request);
        if (response != null && response.getSoundResponse() != null) {
            exchange.requireSuccess(MessageCategoryType.SOUND,
                    response.getSoundResponse().getResponse());
        }
        return null;
    }
}
