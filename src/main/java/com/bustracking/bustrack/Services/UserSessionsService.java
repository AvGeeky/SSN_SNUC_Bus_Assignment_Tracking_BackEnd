package com.bustracking.bustrack.Services;
import java.util.List;
import java.util.UUID;

import com.bustracking.bustrack.Services.GPSService.NeoTrackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bustracking.bustrack.mappings.UserSessionsMapping;
import com.bustracking.bustrack.entities.User_sessions;
@Service
public class UserSessionsService {
  private final  UserSessionsMapping mapper;
  @Autowired
  public UserSessionsService(UserSessionsMapping mapper){
      this.mapper=mapper;
  }
  private static final Logger log = LoggerFactory.getLogger(NeoTrackService.class);
  public User_sessions getsessionbyusername(String username){
      return mapper.getByusername(username);
  }
//  public List<User_sessions> getAll(){
//      return mapper.getAll();
//  }
  @Transactional
   public Boolean create_session(User_sessions session){
        mapper.deleteByUsername(session.getUsername());
        int rows_affected=mapper.insertUser_sessions(session);
        return rows_affected>0;
  }
  @Transactional
    public Boolean update_session(UUID id,String username,String password,String login_type){
      int rows_affected=mapper.updateUser_sesions(id,username,password,login_type);
      return rows_affected>0;
  }
  @Transactional
    public Boolean delete_session(UUID id){
      int rows_affected=mapper.deleteUser_sessions(id);
      return rows_affected>0;
  }
  @Transactional
    public List<User_sessions> getAllData(){
      return mapper.getAllData();
  }

  @Scheduled(cron = "0 0 0 */3 * *")
  @Transactional
  public void cleanupExpiredSessions() {
    int deleted = mapper.deleteExpiredSessions();
    if (deleted > 0) {
      log.info("Deleted {} expired sessions", deleted);
    } else {
      log.debug("No expired sessions found.");
    }
  }


}
