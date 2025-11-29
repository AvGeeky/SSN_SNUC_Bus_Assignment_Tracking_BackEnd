package com.bustracking.bustrack.Services;
import com.bustracking.bustrack.entities.Vehicle_rno_mapping;
import org.apache.ibatis.annotations.*;
import com.bustracking.bustrack.mappings.VehicleRnoMapping;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VehicleRnoService {
    private final VehicleRnoMapping mapper;
    public VehicleRnoService(VehicleRnoMapping mapper){
        this.mapper=mapper;
    }
    public Vehicle_rno_mapping getById(UUID id){
        return mapper.getById(id);
    }
    @Transactional
    public Boolean create_vehicle_rno_mapping(Vehicle_rno_mapping map){
        int rows_affected= mapper.insertMappings(map);
        return rows_affected>0;
    }
    @Transactional
    public Boolean delete_vehicle_rno_mapping(UUID id){
        int rows_affected= mapper.deleteMapping(id);
        return rows_affected>0;
    }
    @Transactional
    public Boolean update_vehicle_rno_mappings(Vehicle_rno_mapping map){
        int rows_affected=mapper.updateMapping(map);
        return rows_affected>0;

    }
    public List<Vehicle_rno_mapping> getAll(){
        return mapper.getAll();
    }
}
