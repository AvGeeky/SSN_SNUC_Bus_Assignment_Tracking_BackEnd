package com.bustracking.bustrack.Services;
import com.bustracking.bustrack.entities.Stop;
import com.bustracking.bustrack.mappings.BusMapping;
import com.bustracking.bustrack.entities.Bus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
@Service
public class BusService {
    private final BusMapping busMapper;

    public BusService(BusMapping stopMapper) {
        this.busMapper = stopMapper;
    }
    public Bus getById(UUID id){
        return busMapper.getbyId(id);
    }
    public List<Bus> getAll(){
        return busMapper.getAll();
    }
    @Transactional
    public Boolean create_bus(Bus bus){
        bus.setId(UUID.randomUUID());
        int rows_affected=busMapper.insert_bus(bus);
        return rows_affected>0;
    }
    @Transactional
    public Boolean delete_bus(UUID id){
        int rows_affected=busMapper.delete_bus(id);
        return rows_affected>0;
    }
    @Transactional
    public Boolean update_bus_capacity(Bus bus){
        int rows_affected=busMapper.update_bus_capacity(bus);
        return rows_affected>0;
    }
}
