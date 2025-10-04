package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.mappings.RiderMapping;
import com.bustracking.bustrack.entities.Rider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RiderService {
    private final RiderMapping riderMapper;
    public RiderService(RiderMapping riderMapper) {
        this.riderMapper = riderMapper;
    }
    public Rider getById(UUID id){
        return riderMapper.getbyId(id);
    }
    public List<Rider> getAll(){
        return riderMapper.getAll();
    }
    @Transactional
    public Boolean create_rider(Rider rider){
        rider.setId(UUID.randomUUID());
        int rows_affected=riderMapper.insert_rider(rider);
        return rows_affected>0;
    }
    @Transactional
    public Boolean delete_rider(UUID id){
        int rows_affected=riderMapper.delete_rider(id);
        return rows_affected>0;
    }
    @Transactional
    public Boolean update_rider(Rider rider){
        int rows_affected=riderMapper.update_rider(rider);
        return rows_affected>0;
    }

    public Rider getByEmail(String email) {
        return riderMapper.findByEmail(email).orElse(null);
    }
}
