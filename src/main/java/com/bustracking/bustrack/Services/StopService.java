package com.bustracking.bustrack.Services;
import com.bustracking.bustrack.mappings.StopMapping;
import com.bustracking.bustrack.entities.Stop;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
@Service
public class StopService {

    private final StopMapping stopMapper;

    public StopService(StopMapping stopMapper) {
        this.stopMapper = stopMapper;
    }
    public Stop getById(UUID id){
        return stopMapper.getById(id);
    }
    public List<Stop> getAll(){
        return stopMapper.getAll();
    }
    @Transactional
    public Boolean create_stop(Stop stop){
        stop.setId(UUID.randomUUID());
        int rows_affected=stopMapper.insertStop(stop);
         return rows_affected>0;
    }
    @Transactional
    public Boolean delete_stop(UUID id){
        int rows_affected=stopMapper.delete_stop(id);
        return rows_affected>0;
    }
    @Transactional
    public Boolean update_stop(Stop stop){
        int rows_affected=stopMapper.update_stop(stop);
        return rows_affected>0;
    }
}
