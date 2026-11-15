package com.nexum.service;

import com.nexum.dto.IaAnalisisLeccionRequest;
import com.nexum.dto.IaAnalisisLeccionResponse;

import java.util.List;
import java.util.UUID;

public interface IaAnalisisLeccionService {
    IaAnalisisLeccionResponse crear(IaAnalisisLeccionRequest request);
    List<IaAnalisisLeccionResponse> listar(UUID usuarioId, UUID leccionId);
    IaAnalisisLeccionResponse obtenerPorId(UUID id);
    IaAnalisisLeccionResponse actualizar(UUID id, IaAnalisisLeccionRequest request);
    void eliminar(UUID id);
}
