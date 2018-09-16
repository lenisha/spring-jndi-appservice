package tutorial;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;



import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Controller
public class UsersController {

    @PersistenceContext
    private EntityManager entityManager;
    protected static final Logger logger = LogManager.getLogger(UsersController.class);

    @RequestMapping("/users")
    public String users(Model model) {

        logger.info("INFO - Retrieveing Users");
        logger.debug("DEBUG - Retrieveing Users");

        model.addAttribute("users", entityManager.createQuery("select u from User u").getResultList());

        return "users";
    }

    @RequestMapping(value = "/create-user", method = RequestMethod.GET)
    public String createUser(Model model) {
        return "create-user";
    }

    @RequestMapping(value = "/create-user", method = RequestMethod.POST)
    @Transactional
    public String createUser(Model model, String name) {

        logger.info("INFO - Creating Users");
        logger.debug("DEBUG - Creating Users");

        User user = new User();
        user.setName(name);

        entityManager.persist(user);

        return "redirect:/users.html";
    }
}
